package com.glamardor.roleplayersquill.speech;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The catalogue of offline recognition models, and where they live on disk.
 *
 * <p>Models are downloaded on demand rather than shipped: the small Russian one
 * alone is bigger than everything else in this mod put together, and most
 * players need exactly one.
 *
 * <p>Each entry names the engine that can run it. There is no separate engine
 * setting on purpose: choosing a model is choosing an engine, and a setting that
 * let the two be picked apart would let them be picked wrong.
 */
public final class SpeechModels {

	/** Which back end a model runs on. */
	public enum Engine {
		/** sherpa-onnx, streaming: words appear as they are said. */
		SHERPA_STREAMING,
		/**
		 * sherpa-onnx, whole utterance at a time: the accurate models, at the
		 * cost of a second or two between the speech and the text.
		 */
		SHERPA_OFFLINE
	}

	/**
	 * One downloadable model.
	 *
	 * @param folder    the directory the archive unpacks into
	 * @param megabytes download size, for telling the player what they are in for
	 */
	public record Entry(String id, String language, String folder, String url, int megabytes, Engine engine) {

		boolean isTarBz2() {
			return url.endsWith(".tar.bz2");
		}
	}

	private static final Map<String, Entry> CATALOGUE = new LinkedHashMap<>();

	private static final String SHERPA = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/";

	static {
		// ── sherpa-onnx, streaming: words appear as they are said ────────────
		streaming("ru-fast", "Русский (быстрая)", "sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16", 23);
		streaming("ru-stream", "Русский (потоковая)", "sherpa-onnx-streaming-zipformer-small-ru-vosk-2025-08-16", 85);
		streaming("ru-tone", "Русский (T-one)", "sherpa-onnx-streaming-t-one-russian-2025-09-08", 123);

		// ── sherpa-onnx, whole utterance: the accurate ones ──────────────────
		// GigaAM writes punctuation and capitals of its own accord, which no
		// other model here does.
		offline("ru-giga", "Русский (GigaAM v3)", "sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16", 156);
		offline("ru-giga-rnnt", "Русский (GigaAM v3 RNN-T)", "sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16", 162);
		// The same lineage as ru-large, exported to ONNX: a fraction of the size
		// and seconds rather than a minute to load.
		offline("ru-big", "Русский (большая ONNX)", "sherpa-onnx-zipformer-ru-2025-04-20", 237);
		offline("ru-big-int8", "Русский (большая ONNX, int8)", "sherpa-onnx-zipformer-ru-int8-2025-04-20", 57);

		// English, and the same idea: NVIDIA's Parakeet writes punctuation and
		// capitals of its own accord, and was trained on the way people actually
		// talk rather than on read-aloud books, which is what the older English
		// models in this catalogue were.
		offline("en-fast", "English (Parakeet)", "sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8", 103);
		// Sold as English and in fact good for twenty-five European languages,
		// Russian among them: worth knowing before anybody downloads two models
		// to cover a server that speaks both.
		offline("en-heavy", "English + 24 more (Parakeet 0.6B)", "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8", 465);
	}

	private SpeechModels() {
	}

	private static void streaming(String id, String language, String folder, int megabytes) {
		add(new Entry(id, language, folder, SHERPA + folder + ".tar.bz2", megabytes, Engine.SHERPA_STREAMING));
	}

	private static void offline(String id, String language, String folder, int megabytes) {
		add(new Entry(id, language, folder, SHERPA + folder + ".tar.bz2", megabytes, Engine.SHERPA_OFFLINE));
	}

	private static void add(Entry entry) {
		CATALOGUE.put(entry.id(), entry);
	}

	public static Map<String, Entry> catalogue() {
		return CATALOGUE;
	}

	public static Entry byId(String id) {
		return CATALOGUE.get(id);
	}

	/**
	 * The model that unpacks into a particular directory.
	 *
	 * <p>For reading a model back out of something that stores it by folder rather
	 * than by id, which is how it sits on disk and how {@link BundledAssets}
	 * carries it inside the jar.
	 */
	public static Entry byFolder(String folder) {
		for (Entry entry : CATALOGUE.values()) {
			if (entry.folder().equals(folder)) {
				return entry;
			}
		}
		return null;
	}

	/**
	 * The model to start with for a language, before anybody has chosen one.
	 *
	 * <p>Russian gets GigaAM rather than the plain Vosk model it shares a name
	 * with: it is the most accurate of the lot, it writes punctuation and
	 * capitals, and it is ready a second after it is asked for. It costs a
	 * larger first download, which is a fair trade for the one thing the mod
	 * exists to do well.
	 */
	public static String defaultFor(String languageCode) {
		String preferred = switch (languageCode) {
			case "ru" -> "ru-giga-rnnt";
			case "en" -> "en-fast";
			default -> languageCode;
		};
		return CATALOGUE.containsKey(preferred) ? preferred : languageCode;
	}

	/** Where models are unpacked: {@code config/roleplayersquill/models}. */
	public static Path modelsDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID).resolve("models");
	}

	/**
	 * Where Voice Subtitles keeps the same models.
	 *
	 * <p>These are the same files from the same place, and the Russian one is 162 MB. Anybody
	 * running both mods has already waited for it once.
	 */
	private static Path sharedDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve("voice_subtitles").resolve("models");
	}

	public static Path pathOf(Entry entry) {
		Path shared = sharedDirectory().resolve(entry.folder());
		if (Files.isDirectory(shared)) {
			return shared;
		}
		return modelsDirectory().resolve(entry.folder());
	}

	/** Written once everything is unpacked, so a half-unpacked model is not mistaken for one. */
	private static final String MARKER = ".installed";

	/**
	 * A model counts as installed once all of it is there.
	 *
	 * <p>This used to ask only whether one file existed, and that was not
	 * enough. A download cut halfway leaves a directory with some of the model
	 * in it, the check passed, and the engine was handed a truncated file – at
	 * which point the native library throws a C++ exception, which takes the
	 * whole game down without a crash report. Two players lost their game to
	 * exactly that.
	 *
	 * <p>So: the marker written at the end of a successful install, or, for
	 * models installed before this mod wrote one, a look at whether the files
	 * add up to something like the size they should.
	 */
	public static boolean isInstalled(Entry entry) {
		Path path = pathOf(entry);
		if (!Files.isDirectory(path)) {
			return false;
		}
		if (Files.isRegularFile(path.resolve(MARKER))) {
			return true;
		}
		boolean shaped = Files.isRegularFile(path.resolve("tokens.txt")) && hasModelFile(path);
		if (!shaped) {
			return false;
		}
		// Three fifths of the download size: these archives hold data that is
		// already compressed, so unpacking them makes them bigger, never much
		// smaller. Anything under that is a model with a piece missing.
		long expected = (long) entry.megabytes() * 1024L * 1024L * 3L / 5L;
		if (sizeOf(path) < expected) {
			return false;
		}
		// It is whole, so say so on disk and never count it again: this walks the
		// folder, and the answer is wanted every time the settings screen closes.
		try {
			markInstalled(entry);
		} catch (IOException ignored) {
		}
		return true;
	}

	/** Marks a model as completely installed. */
	static void markInstalled(Entry entry) throws IOException {
		Files.writeString(pathOf(entry).resolve(MARKER), entry.id() + "\n");
	}

	/** Throws a model away, so the next attempt fetches a whole one. */
	public static void discard(Entry entry) {
		Path path = pathOf(entry);
		try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(file -> {
				try {
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
				}
			});
			RoleplayersQuill.LOGGER.warn("Threw away the model at {}", path);
		} catch (IOException e) {
			RoleplayersQuill.LOGGER.warn("Could not throw away the model at {}", path, e);
		}
	}

	private static boolean hasModelFile(Path path) {
		try (java.util.stream.Stream<Path> files = Files.list(path)) {
			return files.anyMatch(file -> file.getFileName().toString().endsWith(".onnx"));
		} catch (IOException e) {
			return false;
		}
	}

	private static long sizeOf(Path path) {
		try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
			return walk.filter(Files::isRegularFile).mapToLong(file -> {
				try {
					return Files.size(file);
				} catch (IOException e) {
					return 0L;
				}
			}).sum();
		} catch (IOException e) {
			return 0L;
		}
	}

	/**
	 * Downloads and unpacks a model.
	 *
	 * @param progress told how many bytes have arrived so far
	 * @throws Exception if the download or the unpacking fails; the partially
	 *                   written directory is left for inspection rather than
	 *                   silently deleted
	 */
	public static void download(Entry entry, Archives.Progress progress) throws Exception {
		Path models = modelsDirectory();
		Files.createDirectories(models);
		Path archive = models.resolve(entry.folder() + (entry.isTarBz2() ? ".tar.bz2" : ".zip"));

		Archives.download(entry.url(), archive, progress);
		DownloadState.installing();
		if (entry.isTarBz2()) {
			Archives.extractTarBz2(archive, models, progress);
		} else {
			Archives.extractZip(archive, models, progress);
		}
		Files.deleteIfExists(archive);
		markInstalled(entry);
		RoleplayersQuill.LOGGER.info("Model {} installed at {}", entry.id(), pathOf(entry));
	}
}
