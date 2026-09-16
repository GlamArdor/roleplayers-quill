package com.glamardor.roleplayersquill.speech;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.config.QuillConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Talking a page into the book.
 *
 * <p>Everything happens on this machine. The model is an ONNX file on disk and the recognition runs
 * in the same process as the game; no audio leaves the computer, and none of it is stored. The
 * model is not shipped with the mod either – it is 162 MB for the Russian one – so it is fetched
 * the first time dictation is switched on and never before, which is the whole reason the setting
 * exists as a setting rather than a button.
 *
 * <p>Two engines, chosen by which model was picked rather than by a separate setting: a streaming
 * one that shows words as they are said, and an offline one that waits for the end of the sentence
 * and is markedly more accurate. GigaAM, the default for Russian, is the offline kind and writes
 * its own punctuation and capitals, which is worth a second of waiting in a book.
 */
public final class Dictation {
	public enum State {
		OFF, PREPARING, LISTENING, ERROR
	}

	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private static volatile State state = State.OFF;
	private static volatile Text status = Text.empty();
	private static volatile float level;
	private static volatile String partial = "";

	@Nullable
	private static SpeechEngine engine;
	@Nullable
	private static Thread worker;

	private Dictation() {
	}

	public static State state() {
		return state;
	}

	public static Text status() {
		return status;
	}

	/** How loud the microphone is, 0 to 1, for the meter beside the button. */
	public static float level() {
		return level;
	}

	/** What has been heard but not yet finished, all of it. */
	public static String partial() {
		return partial;
	}

	/** How fast the words are typed out on screen, in characters a second. */
	private static final int REVEAL_SPEED = 55;

	private static volatile float revealed;
	private static volatile long revealedAt;

	/**
	 * As much of what has been heard as may be shown yet.
	 *
	 * <p>An offline model settles a whole phrase at once, so the text arrives in slabs: four words
	 * appear together, then nothing for a second, then six more. Reading that is unpleasant in a way
	 * that is hard to name until it stops.
	 *
	 * <p>So the count of shown characters is carried between frames rather than worked out from the
	 * clock. Worked out from the clock it outruns the speaking within a second – nobody talks at
	 * fifty-five characters a second – and from then on every phrase is already allowed in full and
	 * lands whole anyway. Carried, it sits waiting at the end of the text and types each new phrase
	 * out from where the last one stopped. It is also never allowed to fall much more than three
	 * quarters of a second behind, or the tail of a long sentence arrives after the speaker has
	 * finished and stopped caring.
	 */
	public static String visible() {
		String text = partial;
		long now = System.currentTimeMillis();
		long last = revealedAt;
		if (last == 0L) {
			revealedAt = now;
			last = now;
		}
		if (now > last) {
			revealed += (now - last) * REVEAL_SPEED / 1000.0f;
			revealedAt = now;
		}
		int length = text.length();
		if (revealed > length) {
			revealed = length;
		}
		float most = Math.max(20.0f, REVEAL_SPEED * 0.8f);
		if (length - revealed > most) {
			revealed = length - most;
		}
		int shown = Math.max(0, Math.min(length, (int) revealed));
		// Never between the two halves of a surrogate pair, which would draw as a broken glyph.
		if (shown > 0 && shown < length && Character.isHighSurrogate(text.charAt(shown - 1))) {
			shown--;
		}
		return text.substring(0, shown);
	}

	public static boolean isRunning() {
		return RUNNING.get();
	}

	/** Whether the engine and the model are on disk, so that pressing the button starts at once. */
	public static boolean isReady() {
		SpeechModels.Entry entry = SpeechModels.byId(QuillConfig.get().voiceModel);
		if (entry == null) {
			return false;
		}
		SherpaNatives.Build build = SherpaNatives.buildForThisMachine();
		return build != null && SherpaNatives.isInstalled(build) && SpeechModels.isInstalled(entry);
	}

	/** Starts if stopped and stops if started. @return true when it is now listening or preparing to */
	public static boolean toggle(Consumer<String> sink) {
		if (RUNNING.get()) {
			stop();
			return false;
		}
		start(sink);
		return true;
	}

	/**
	 * Begins listening, fetching whatever is missing first.
	 *
	 * @param sink handed the finished text, on the client thread
	 */
	public static void start(Consumer<String> sink) {
		QuillConfig config = QuillConfig.get();
		if (!config.voiceEnabled) {
			fail(Text.translatable("roleplayersquill.voice.disabled"));
			return;
		}
		SpeechModels.Entry entry = SpeechModels.byId(config.voiceModel);
		if (entry == null) {
			fail(Text.translatable("roleplayersquill.voice.no_model", config.voiceModel));
			return;
		}
		if (!RUNNING.compareAndSet(false, true)) {
			return;
		}

		state = State.PREPARING;
		status = Text.translatable("roleplayersquill.voice.preparing");
		partial = "";
		revealed = 0.0f;
		revealedAt = 0L;

		worker = new Thread(() -> run(entry, config, sink), "roleplayers-quill-dictation");
		worker.setDaemon(true);
		worker.start();
	}

	public static void stop() {
		RUNNING.set(false);
		Thread current = worker;
		if (current != null) {
			current.interrupt();
		}
	}

	/** Lets the engine go, which is a hundred-odd megabytes of model. Called when the editor closes. */
	public static synchronized void release() {
		stop();
		SpeechEngine open = engine;
		engine = null;
		if (open != null) {
			try {
				open.close();
			} catch (Throwable error) {
				RoleplayersQuill.LOGGER.debug("The engine complained on the way out", error);
			}
		}
		state = State.OFF;
		partial = "";
		level = 0.0f;
	}

	// ---- the worker ------------------------------------------------------------------------------

	private static void run(SpeechModels.Entry entry, QuillConfig config, Consumer<String> sink) {
		try {
			if (!ensureInstalled(entry, config)) {
				return;
			}
			SpeechEngine open = engineFor(entry);
			if (open == null) {
				return;
			}

			state = State.LISTENING;
			status = Text.translatable("roleplayersquill.voice.listening");

			StringBuilder heard = new StringBuilder();
			try (Microphone microphone = Microphone.open(config.voiceDevice);
					SpeechEngine.Stream stream = open.open()) {
				if (stream == null) {
					fail(Text.translatable("roleplayersquill.voice.engine_failed"));
					return;
				}
				while (RUNNING.get()) {
					short[] pcm = microphone.read();
					if (pcm.length == 0) {
						continue;
					}
					level = Microphone.level(pcm);
					String finished = stream.accept(pcm);
					if (finished != null && !finished.isBlank()) {
						append(heard, finished);
					}
					partial = join(heard.toString(), stream.partial());
				}
				String tail = stream.settle();
				if (tail != null && !tail.isBlank()) {
					append(heard, tail);
				}
			}

			String result = tidy(heard.toString(), config.voiceTidyUp);
			partial = "";
			level = 0.0f;
			state = State.OFF;
			status = Text.empty();
			if (!result.isBlank()) {
				MinecraftClient.getInstance().execute(() -> sink.accept(result));
			}
		} catch (javax.sound.sampled.LineUnavailableException error) {
			RoleplayersQuill.LOGGER.warn("No microphone for dictation", error);
			fail(Text.translatable("roleplayersquill.voice.no_microphone"));
		} catch (Throwable error) {
			RoleplayersQuill.LOGGER.error("Dictation stopped on an error", error);
			fail(Text.literal(String.valueOf(error.getMessage())));
		} finally {
			RUNNING.set(false);
			worker = null;
			level = 0.0f;
		}
	}

	/** Fetches the engine and the model if they are not there, and says so while it does. */
	private static boolean ensureInstalled(SpeechModels.Entry entry, QuillConfig config) {
		SherpaNatives.Build build = SherpaNatives.buildForThisMachine();
		if (build == null) {
			fail(Text.translatable("roleplayersquill.voice.no_build"));
			return false;
		}
		if (!SherpaNatives.isInstalled(build)) {
			if (!config.voiceAutoDownload) {
				fail(Text.translatable("roleplayersquill.voice.needs_download"));
				return false;
			}
			status = Text.translatable("roleplayersquill.voice.fetching_engine", build.megabytes());
			if (!awaitDownload(() -> ModelDownload.startNativesOnce(build, () -> {
			}))) {
				return false;
			}
			if (!SherpaNatives.isInstalled(build)) {
				fail(Text.translatable("roleplayersquill.voice.engine_failed"));
				return false;
			}
		}
		if (!SpeechModels.isInstalled(entry)) {
			if (!config.voiceAutoDownload) {
				fail(Text.translatable("roleplayersquill.voice.needs_download"));
				return false;
			}
			status = Text.translatable("roleplayersquill.voice.fetching_model", entry.megabytes());
			if (!awaitDownload(() -> ModelDownload.startOnce(entry, () -> {
			}))) {
				return false;
			}
			if (!SpeechModels.isInstalled(entry)) {
				fail(Text.translatable("roleplayersquill.voice.model_failed"));
				return false;
			}
		}
		return true;
	}

	/** Kicks a download off and waits for it, giving up the moment the player cancels. */
	private static boolean awaitDownload(java.util.function.BooleanSupplier begin) {
		if (!begin.getAsBoolean() && !ModelDownload.isRunning()) {
			fail(Text.translatable("roleplayersquill.voice.busy"));
			return false;
		}
		while (ModelDownload.isRunning()) {
			if (!RUNNING.get()) {
				return false;
			}
			try {
				Thread.sleep(200L);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return RUNNING.get();
	}

	private static synchronized SpeechEngine engineFor(SpeechModels.Entry entry) {
		if (engine != null && engine.modelId().equals(entry.id())) {
			return engine;
		}
		if (engine != null) {
			engine.close();
			engine = null;
		}
		status = Text.translatable("roleplayersquill.voice.loading");
		// The lock file is written before the native library is asked to open a model and cleared
		// afterwards, so a model file that takes the process down with it is known about next start.
		CrashGuard.begin(entry.id());
		try {
			engine = entry.engine() == SpeechModels.Engine.SHERPA_OFFLINE
					? SherpaOfflineEngine.load(entry)
					: SherpaEngine.load(entry);
		} catch (Throwable error) {
			RoleplayersQuill.LOGGER.error("Could not open the model {}", entry.id(), error);
			fail(Text.translatable("roleplayersquill.voice.model_failed"));
			return null;
		} finally {
			CrashGuard.end();
		}
		return engine;
	}

	private static void fail(Text message) {
		state = State.ERROR;
		status = message;
		RUNNING.set(false);
	}

	// ---- tidying up ------------------------------------------------------------------------------

	private static void append(StringBuilder heard, String piece) {
		String trimmed = piece.strip();
		if (trimmed.isEmpty()) {
			return;
		}
		if (heard.length() > 0 && heard.toString().strip().endsWith(trimmed)) {
			// The engine handed the same window back; once is enough.
			return;
		}
		if (heard.length() > 0) {
			heard.append(' ');
		}
		heard.append(trimmed);
	}

	private static String join(String finished, String partial) {
		if (partial == null || partial.isBlank()) {
			return finished;
		}
		return finished.isBlank() ? partial.strip() : finished + " " + partial.strip();
	}

	/**
	 * A capital at the front and a full stop at the back.
	 *
	 * <p>Only where the model has not done it already: GigaAM and Parakeet punctuate their own
	 * output, and a second full stop after theirs would be this mod's fault, not theirs.
	 */
	private static String tidy(String text, boolean enabled) {
		String result = text.strip().replaceAll("\\s+", " ");
		if (!enabled || result.isEmpty()) {
			return result;
		}
		if (Character.isLetter(result.charAt(0))) {
			result = result.substring(0, 1).toUpperCase(Locale.ROOT) + result.substring(1);
		}
		char last = result.charAt(result.length() - 1);
		if (Character.isLetterOrDigit(last)) {
			result = result + ".";
		}
		return result;
	}
}
