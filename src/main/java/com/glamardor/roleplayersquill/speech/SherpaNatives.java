package com.glamardor.roleplayersquill.speech;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.k2fsa.sherpa.onnx.LibraryLoader;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The native library sherpa-onnx runs on, fetched on demand.
 *
 * <p>It is not shipped inside the mod for the same reason the models are not:
 * six platform builds would be most of the download for everybody, and each
 * player needs exactly one. Unlike a model, though, this is executable code, so
 * the archive is checked against a hash pinned here before anything is unpacked
 * or loaded. The hashes come from the release itself and are tied to
 * {@link #VERSION}: bumping one without the other is a mistake worth failing on.
 */
public final class SherpaNatives {

	/** The sherpa-onnx release the vendored Java classes were taken from. */
	public static final String VERSION = "v1.13.4";

	private static final String BASE =
			"https://github.com/k2-fsa/sherpa-onnx/releases/download/" + VERSION + "/sherpa-onnx-" + VERSION + "-";

	/**
	 * One build of the native library.
	 *
	 * @param sha256 taken from the release asset digest; the archive is refused
	 *               if it does not match
	 */
	public record Build(String platform, String sha256, int megabytes) {

		String archiveName() {
			return "sherpa-onnx-" + VERSION + "-" + platform + "-jni.tar.bz2";
		}

		String url() {
			return BASE + platform + "-jni.tar.bz2";
		}

		/** The directory the archive unpacks into. */
		String folder() {
			return "sherpa-onnx-" + VERSION + "-" + platform + "-jni";
		}
	}

	private static final Map<String, Build> BUILDS = Map.of(
			"win-x64", new Build("win-x64", "78202df9c017fd733d8d78a1fa9402410b22e3aa347288bda9a1a3b3fc5278c3", 7),
			"win-arm64", new Build("win-arm64", "75ffc955c34203ff7f9f1007080f6dcb14cc907441a8b4185f2fa9d0e7ef8220", 7),
			"linux-x64", new Build("linux-x64", "b9c42f06d72d5df344923f387dd02cfd530825118d1ee663f0349f99e7d55c5c", 26),
			"linux-aarch64", new Build("linux-aarch64", "94e0bec829ff09afcf9630f3ddd87e7b50a255545e91f6b365b15a8a4738d5da", 12),
			"osx-arm64", new Build("osx-arm64", "f9997168dea5a42735310928868f1752067fb23b9b981dca24c347c1fb639c19", 27),
			"osx-x86_64", new Build("osx-x86_64", "75660947c17e3b0d6f5b640acfc337de7ca9fbb78e03eed6a17b14beeb5d2c8b", 30));

	private static volatile boolean loaded;
	private static volatile String failure = "";

	private SherpaNatives() {
	}

	/** @return the build for this machine, or {@code null} on a platform with none */
	public static Build buildForThisMachine() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

		boolean arm = arch.contains("aarch64") || arch.contains("arm64");
		if (os.contains("win")) {
			return BUILDS.get(arm ? "win-arm64" : "win-x64");
		}
		if (os.contains("mac") || os.contains("darwin")) {
			return BUILDS.get(arm ? "osx-arm64" : "osx-x86_64");
		}
		if (os.contains("nux") || os.contains("nix")) {
			return BUILDS.get(arm ? "linux-aarch64" : "linux-x64");
		}
		return null;
	}

	/**
	 * Where the unpacked libraries live: {@code config/roleplayersquill/native}.
	 *
	 * <p>Unless Voice Subtitles has already put the same build of the same library in its own
	 * folder, in which case that is used and nothing is downloaded. It is the same file from the
	 * same release, and it is a hundred megabytes.
	 */
	public static Path directory() {
		Path shared = FabricLoader.getInstance().getConfigDir().resolve("voice_subtitles").resolve("native");
		Build build = buildForThisMachine();
		if (build != null && java.nio.file.Files.isDirectory(shared.resolve(build.folder()))) {
			return shared;
		}
		return FabricLoader.getInstance().getConfigDir()
				.resolve(com.glamardor.roleplayersquill.RoleplayersQuill.MOD_ID).resolve("native");
	}

	public static boolean isInstalled(Build build) {
		return build != null && Files.isDirectory(directory().resolve(build.folder()).resolve("lib"));
	}

	/**
	 * Where the unpacked libraries for this machine are, or {@code null} if they
	 * are not there.
	 *
	 * <p>Public because the translator needs one of them: onnxruntime is the same
	 * library either way, and there is no sense downloading a second copy of it
	 * once this one is on disk.
	 */
	public static Path libraryDirectory() {
		Build build = buildForThisMachine();
		if (build == null) {
			return null;
		}
		Path lib = directory().resolve(build.folder()).resolve("lib");
		return Files.isDirectory(lib) ? lib : null;
	}

	/**
	 * The onnxruntime shared library itself, as opposed to sherpa's binding to it.
	 *
	 * @return the file, or {@code null} when it is not on disk
	 */
	public static Path onnxRuntimeLibrary() {
		Path lib = libraryDirectory();
		if (lib == null) {
			return null;
		}
		try (Stream<Path> files = Files.list(lib)) {
			return files.filter(path -> {
				String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
				return name.contains("onnxruntime") && !name.contains("providers") && !name.contains("sherpa")
						&& (name.endsWith(".dll") || name.endsWith(".dylib") || name.contains(".so"));
			}).findFirst().orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	public static boolean isLoaded() {
		return loaded;
	}

	public static String failure() {
		return failure;
	}

	/** Downloads and unpacks the library for this machine. */
	public static void download(Build build, Archives.Progress progress) throws Exception {
		Path root = directory();
		Files.createDirectories(root);
		Path archive = root.resolve(build.archiveName());

		Archives.download(build.url(), archive, progress);
		DownloadState.installing();
		String actual = Archives.sha256(archive);
		if (!actual.equalsIgnoreCase(build.sha256())) {
			Files.deleteIfExists(archive);
			throw new IOException("Checksum mismatch for " + build.archiveName()
					+ ": expected " + build.sha256() + ", got " + actual);
		}
		Archives.extractTarBz2(archive, root, progress);
		Files.deleteIfExists(archive);
		RoleplayersQuill.LOGGER.info("sherpa-onnx {} native library installed at {}", VERSION, root.resolve(build.folder()));
	}

	/**
	 * Loads the library into the JVM. Once only; a JVM cannot unload it, so a
	 * second call after a failure will not help either.
	 *
	 * @return true when sherpa-onnx can be used
	 */
	public static synchronized boolean load() {
		if (loaded) {
			return true;
		}
		if (!failure.isEmpty()) {
			return false;
		}
		Build build = buildForThisMachine();
		if (build == null) {
			failure = "no sherpa-onnx build for " + System.getProperty("os.name") + " / " + System.getProperty("os.arch");
			return false;
		}
		Path lib = directory().resolve(build.folder()).resolve("lib");
		if (!Files.isDirectory(lib)) {
			failure = "sherpa-onnx native library is not installed";
			return false;
		}

		try {
			// onnxruntime first: the JNI library is linked against it, and on
			// Windows the loader will not find it just because it sits in the
			// same folder.
			for (Path library : orderedLibraries(lib)) {
				System.load(library.toAbsolutePath().toString());
			}
			// Ours are loaded, so their own loader must not go looking for more.
			LibraryLoader.setAutoLoadEnabled(false);
			loaded = true;
			RoleplayersQuill.LOGGER.info("sherpa-onnx {} loaded from {}", VERSION, lib);
			return true;
		} catch (Throwable t) {
			// UnsatisfiedLinkError as much as anything else: a missing C runtime,
			// a CPU without the instructions the build wants, an antivirus that
			// ate the file. None of it may take the game down.
			failure = t.getClass().getSimpleName() + ": " + t.getMessage();
			RoleplayersQuill.LOGGER.error("Could not load the sherpa-onnx native library", t);
			return false;
		}
	}

	/**
	 * The shared libraries in load order: onnxruntime, then everything else,
	 * with the JNI binding last.
	 *
	 * <p>Found by looking rather than by name, because the exact file names
	 * differ per platform and per onnxruntime version.
	 */
	private static List<Path> orderedLibraries(Path lib) throws IOException {
		List<Path> runtime = new ArrayList<>();
		List<Path> jni = new ArrayList<>();
		try (Stream<Path> files = Files.list(lib)) {
			for (Path path : files.toList()) {
				String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
				if (!name.endsWith(".dll") && !name.endsWith(".dylib") && !name.contains(".so")) {
					continue; // .lib import stubs and anything else are not ours to load
				}
				if (name.contains("sherpa-onnx-jni")) {
					jni.add(path);
				} else if (name.contains("onnxruntime") && !name.contains("providers")) {
					runtime.add(path);
				}
			}
		}
		List<Path> ordered = new ArrayList<>(runtime);
		ordered.addAll(jni);
		if (jni.isEmpty()) {
			throw new IOException("No sherpa-onnx-jni library in " + lib);
		}
		return ordered;
	}
}
