package com.glamardor.roleplayersquill.speech;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Surviving a model that takes the game down with it.
 *
 * <p>Opening a model happens inside a native library, and a native library that
 * gives up throws a C++ exception, not a Java one. Nothing in Java can catch
 * that: the process is gone before any {@code catch} is reached, with no crash
 * report and no stack trace, only a line in the launcher saying the JVM died in
 * native code. Two players hit exactly that, both a second after the library
 * loaded and before any model had finished opening.
 *
 * <p>Whatever the reason – a download cut short, a file an antivirus took a
 * bite out of, a disk that lied about writing – the unbearable part is the
 * second launch, which does the same thing again, and the third. So a note is
 * left on disk saying what is being opened, and removed once it opens. Finding
 * that note at startup means the last attempt did not survive it.
 *
 * <p>Once is enough. Forgiving the first failure and trying again was the first
 * attempt at being polite about it – the same note is left behind by anybody who
 * closes the game while a model is loading – and it meant the game died a second
 * time before anything was done. Nobody thanks you for that. So the model is set
 * aside on the first failure and the game starts without it, saying so; putting
 * it back is one command, and it costs whoever closed their game impatiently
 * exactly that.
 */
public final class CrashGuard {

	private static final Path LOCK = FabricLoader.getInstance().getConfigDir()
			.resolve(com.glamardor.roleplayersquill.RoleplayersQuill.MOD_ID).resolve("loading.lock");

	/** What was being opened when the game last died, or {@code null}. */
	private static volatile String suspect;
	/** How many times in a row that same thing has failed to open. */
	private static volatile int strikes;

	private CrashGuard() {
	}

	/** Reads what the last run left behind. Called once, before anything loads. */
	public static void init() {
		try {
			if (!Files.isRegularFile(LOCK)) {
				return;
			}
			List<String> lines = Files.readAllLines(LOCK);
			suspect = lines.isEmpty() ? null : lines.get(0).trim();
			strikes = lines.size() > 1 ? parse(lines.get(1)) : 1;
			// Left on disk on purpose. Deleting it here meant the suspicion lasted
			// one run: the game was set aside, started, and the run after that
			// tried the same file again with the same result.
			if (suspect != null && !suspect.isEmpty()) {
				RoleplayersQuill.LOGGER.warn("The game did not survive opening '{}' last time; leaving it alone",
						suspect);
			}
		} catch (IOException e) {
			suspect = null;
		}
	}

	/** @return what died last time, or {@code null} */
	public static String suspect() {
		return suspect;
	}

	/** @return true when this is the thing that took the game down */
	public static boolean isSuspect(String tag) {
		return tag != null && tag.equals(suspect);
	}

	/** Forgets the suspicion, and the note on disk with it. */
	public static void forgive() {
		suspect = null;
		strikes = 0;
		end();
	}

	/**
	 * Notes that something is being opened.
	 *
	 * <p>Written synchronously and on purpose: the whole point is that it is on
	 * disk before the library is asked to do anything.
	 */
	public static void begin(String tag) {
		try {
			Files.createDirectories(LOCK.getParent());
			int attempt = isSuspect(tag) ? strikes + 1 : 1;
			Files.writeString(LOCK, tag + "\n" + attempt + "\n");
		} catch (IOException ignored) {
			// A note we cannot write is a protection we do not get, which is
			// exactly where this mod was before. Not worth failing the load over.
		}
	}

	/** Notes that it opened. */
	public static void end() {
		try {
			Files.deleteIfExists(LOCK);
		} catch (IOException ignored) {
		}
	}

	private static int parse(String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 1;
		}
	}
}
