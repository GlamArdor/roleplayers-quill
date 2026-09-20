package com.glamardor.roleplayersquill.book;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.speech.Archives;
import com.glamardor.roleplayersquill.speech.DownloadState;
import com.glamardor.roleplayersquill.text.Spelling;
import com.glamardor.roleplayersquill.text.WordList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetching a spelling list and turning it into something the game can carry.
 *
 * <p>The same shape as the dictation download and for the same reasons: one at a time, on a thread
 * of its own, with a toast in the corner and a bar at the bottom of the screen, because eighteen
 * megabytes on a hotel connection is long enough that a game with no sign of life on it looks like a
 * game that has stopped.
 *
 * <p>The list itself is thrown away as soon as it has been read. What stays is the index – twelve
 * megabytes of hashes – which is the only part any of this needs afterwards.
 */
public final class DictionaryDownload {

	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private DictionaryDownload() {
	}

	public static boolean isRunning() {
		return RUNNING.get();
	}

	/**
	 * Fetches the languages that are wanted and not here yet.
	 *
	 * @return false when nothing had to be fetched or something else is already fetching
	 */
	public static boolean startOnce(List<Spelling.Tongue> wanted, Runnable onFinished) {
		if (wanted.isEmpty() || !RUNNING.compareAndSet(false, true)) {
			return false;
		}

		int megabytes = 0;
		for (Spelling.Tongue tongue : wanted) {
			megabytes += tongue.megabytes();
		}
		toast(Text.translatable("toast.roleplayersquill.dictionary.downloading"),
				Text.translatable("toast.roleplayersquill.dictionary.size", megabytes));

		Thread thread = new Thread(() -> {
			try {
				for (Spelling.Tongue tongue : wanted) {
					fetch(tongue);
				}
				toast(Text.translatable("toast.roleplayersquill.dictionary.ready"),
						Text.translatable("toast.roleplayersquill.dictionary.ready.detail"));
				MinecraftClient.getInstance().execute(onFinished);
			} catch (Exception error) {
				RoleplayersQuill.LOGGER.error("Could not fetch a spelling dictionary", error);
				toast(Text.translatable("toast.roleplayersquill.dictionary.failed"),
						Text.literal(String.valueOf(error.getMessage())).formatted(Formatting.RED));
			} finally {
				DownloadState.end();
				RUNNING.set(false);
			}
		}, "roleplayers-quill-dictionary-download");
		thread.setDaemon(true);
		thread.start();
		return true;
	}

	private static void fetch(Spelling.Tongue tongue) throws Exception {
		Path list = Spelling.listOf(tongue);
		Path index = Spelling.indexOf(tongue);
		Files.createDirectories(list.getParent());

		DownloadState.begin(Text.translatable("roleplayersquill.download.dictionary",
				tongue.label().getString()));
		Archives.download(tongue.url(), list, DownloadState::update);

		// Reading a million and a half words and sorting their hashes takes a second or two, which is
		// a second or two after a bar has reached the end. Saying which part is which is the whole
		// difference between waiting and wondering.
		DownloadState.installing();
		WordList built = WordList.build(list, index, tongue.cyrillic());
		Files.deleteIfExists(list);
		RoleplayersQuill.LOGGER.info("Spelling: built {} with {} words", index, built.size());
		Spelling.reload(tongue);
	}

	private static void toast(Text title, Text description) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> client.getToastManager().add(
				new SystemToast(SystemToast.Type.PERIODIC_NOTIFICATION, title, description)));
	}
}
