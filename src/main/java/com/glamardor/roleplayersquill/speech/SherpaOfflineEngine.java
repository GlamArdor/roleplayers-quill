package com.glamardor.roleplayersquill.speech;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * sherpa-onnx running a whole-utterance model.
 *
 * <p>These are the accurate ones – GigaAM and the big Russian zipformer – and
 * they are not streaming: a model like this wants the sentence in front of it
 * before it will say anything, and it looks both ways through the audio to
 * decide what a word was. That is where the accuracy comes from, and it is also
 * why it cannot type along with the speaker.
 *
 * <p>So audio is kept in a buffer and decoded repeatedly: often enough that a
 * caption still grows while somebody talks, rarely enough that a slow machine is
 * not buried under the work. How rarely follows from how long the last decode
 * took on this machine, rather than from a number guessed here.
 */
final class SherpaOfflineEngine implements SpeechEngine {

	private static final int RATE = 16_000;

	/** Never decode more often than this, however fast the machine is. */
	private static final long MIN_INTERVAL_MS = 700L;

	/** Decoding costs this many times its own duration in cooldown. */
	private static final int COOLDOWN_FACTOR = 3;

	/**
	 * An utterance this long is cut and reported by itself.
	 *
	 * <p>Generous on purpose. Somebody telling a story barely pauses, and at
	 * twenty seconds their sentence was being guillotined mid-word: the caption
	 * ended on "и она, оказывается, за" and the rest turned up as a separate
	 * line. Two minutes covers any monologue worth calling one.
	 */
	private static final int MAX_UTTERANCE_SECONDS = 120;

	/**
	 * The most audio a running decode looks at, in seconds.
	 *
	 * <p>Decoding costs time in proportion to the audio, so re-reading a whole
	 * monologue every time would slow to a crawl exactly when somebody is
	 * talking most. While speech is in progress only the recent part is decoded,
	 * which keeps that cost flat; the final pass at the end of the utterance
	 * reads everything and produces the sentence in full.
	 */
	private static final int LIVE_WINDOW_SECONDS = 20;

	private final String modelId;
	private final OfflineRecognizer recognizer;

	private SherpaOfflineEngine(String modelId, OfflineRecognizer recognizer) {
		this.modelId = modelId;
		this.recognizer = recognizer;
	}

	static SherpaOfflineEngine load(SpeechModels.Entry entry) throws IOException {
		if (!SherpaNatives.load()) {
			throw new IllegalStateException("sherpa-onnx native library unavailable: " + SherpaNatives.failure());
		}
		Path dir = SpeechModels.pathOf(entry);
		Path tokens = dir.resolve("tokens.txt");
		if (!Files.isRegularFile(tokens)) {
			throw new IOException("No tokens.txt in " + dir);
		}
		// Said out loud before the library is asked to do anything, so that a log
		// from a game that did not survive the next line still says what it was
		// given.
		RoleplayersQuill.LOGGER.info("Opening the '{}' model in {}", entry.id(), dir);

		OfflineModelConfig.Builder model = OfflineModelConfig.builder()
				.setTokens(tokens.toString())
				// Two threads: these models are heavier than the streaming ones,
				// and one thread leaves captions trailing the conversation.
				.setNumThreads(2)
				.setDebug(false);

		Path single = SherpaEngine.findModelFile(dir, "model");
		if (single != null) {
			model.setNemo(OfflineNemoEncDecCtcModelConfig.builder().setModel(single.toString()).build());
		} else {
			model.setTransducer(OfflineTransducerModelConfig.builder()
					.setEncoder(SherpaEngine.requireModelFile(dir, "encoder").toString())
					.setDecoder(SherpaEngine.requireModelFile(dir, "decoder").toString())
					.setJoiner(SherpaEngine.requireModelFile(dir, "joiner").toString())
					.build());
		}

		OfflineRecognizer recognizer = new OfflineRecognizer(OfflineRecognizerConfig.builder()
				.setOfflineModelConfig(model.build())
				.setDecodingMethod("greedy_search")
				.build());
		RoleplayersQuill.LOGGER.info("sherpa-onnx offline model '{}' loaded from {}", entry.id(), dir);
		return new SherpaOfflineEngine(entry.id(), recognizer);
	}

	@Override
	public String modelId() {
		return modelId;
	}

	@Override
	public Stream open() {
		return new BufferedStream();
	}

	@Override
	public void close() {
		try {
			recognizer.release();
		} catch (Throwable ignored) {
		}
	}

	/**
	 * @param from   first sample to decode, for reading only the recent part
	 * @return the text for these samples, or an empty string if there are none
	 */
	private String decode(float[] samples, int from, int length) {
		int count = length - from;
		if (count <= 0) {
			return "";
		}
		float[] audio = new float[count];
		System.arraycopy(samples, from, audio, 0, count);
		OfflineStream stream = recognizer.createStream();
		try {
			stream.acceptWaveform(audio, RATE);
			recognizer.decode(stream);
			return recognizer.getResult(stream).getText().trim();
		} finally {
			try {
				stream.release();
			} catch (Throwable ignored) {
			}
		}
	}

	/** One speaker's audio, held until there is a reason to decode it. */
	private final class BufferedStream implements Stream {

		private float[] buffer = new float[RATE * 4];
		private int length;
		private long lastDecodeAt;
		private long lastDecodeTook;
		private String lastText = "";

		@Override
		public String accept(short[] pcm16k) {
			append(pcm16k);
			if (length >= RATE * MAX_UTTERANCE_SECONDS) {
				// Long enough to be a sentence in its own right, whether or not
				// the speaker thinks so.
				String text = run(true);
				reset();
				return text;
			}
			return null;
		}

		@Override
		public String partial() {
			long now = System.currentTimeMillis();
			long cooldown = Math.max(MIN_INTERVAL_MS, lastDecodeTook * COOLDOWN_FACTOR);
			if (now - lastDecodeAt < cooldown) {
				// Too soon: hand back what the last decode said rather than
				// spending the CPU again on almost the same audio.
				return lastText;
			}
			return run();
		}

		@Override
		public String settle() {
			// The final pass reads the whole utterance, however long it ran.
			String text = run(true);
			reset();
			return text;
		}

		private String run() {
			return run(false);
		}

		private String run(boolean whole) {
			// Mid-speech, start from the last window rather than the beginning:
			// the caption is showing the recent words anyway, and the cost of a
			// decode then stops growing with the length of the monologue.
			int from = whole ? 0 : Math.max(0, length - RATE * LIVE_WINDOW_SECONDS);
			long started = System.currentTimeMillis();
			try {
				lastText = decode(buffer, from, length);
			} catch (Throwable t) {
				RoleplayersQuill.LOGGER.error("Offline decode failed", t);
				lastText = "";
			}
			lastDecodeTook = System.currentTimeMillis() - started;
			lastDecodeAt = System.currentTimeMillis();
			return lastText;
		}

		private void append(short[] pcm) {
			if (length + pcm.length > buffer.length) {
				int size = Math.max(buffer.length * 2, length + pcm.length);
				float[] bigger = new float[size];
				System.arraycopy(buffer, 0, bigger, 0, length);
				buffer = bigger;
			}
			for (short sample : pcm) {
				buffer[length++] = sample / 32768F;
			}
		}

		private void reset() {
			length = 0;
			lastText = "";
			lastDecodeTook = 0L;
			lastDecodeAt = 0L;
		}

		@Override
		public void close() {
			buffer = null;
		}
	}
}
