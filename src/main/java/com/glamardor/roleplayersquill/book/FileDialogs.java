package com.glamardor.roleplayersquill.book;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The system's own open and save dialogs.
 *
 * <p>Minecraft already carries the library for these and uses it itself, so importing a chapter
 * means picking the file where it actually is rather than being told to copy it into a particular
 * folder first.
 *
 * <p>They block the thread they are called on for as long as the window is up, which on the render
 * thread would be the game frozen behind a file picker. So they are opened on a thread of their own
 * and the answer is handed back on the client thread.
 */
public final class FileDialogs {
	private FileDialogs() {
	}

	/** Asks for a file to read. The callback runs on the client thread, with null for a cancel. */
	public static void open(String title, String[] patterns, String description, Consumer<Path> whenChosen) {
		ask(() -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(patterns.length);
				for (String pattern : patterns) {
					filters.put(stack.UTF8(pattern));
				}
				filters.flip();
				return TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath(), filters, description, false);
			}
		}, whenChosen);
	}

	/** Asks where to write. */
	public static void save(String title, String suggestedName, String[] patterns, String description,
			Consumer<Path> whenChosen) {
		ask(() -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(patterns.length);
				for (String pattern : patterns) {
					filters.put(stack.UTF8(pattern));
				}
				filters.flip();
				return TinyFileDialogs.tinyfd_saveFileDialog(title,
						BookIO.exportDir().resolve(suggestedName).toString(), filters, description);
			}
		}, whenChosen);
	}

	private static String defaultPath() {
		return BookIO.exportDir().toString() + java.io.File.separator;
	}

	private static void ask(java.util.function.Supplier<String> dialog, Consumer<Path> whenChosen) {
		Thread thread = new Thread(() -> {
			String chosen = null;
			try {
				chosen = dialog.get();
			} catch (Throwable error) {
				// No dialog on this system, or the library is missing: not a reason to lose the book.
				RoleplayersQuill.LOGGER.warn("The file dialog could not be opened", error);
			}
			Path result = chosen == null || chosen.isBlank() ? null : Path.of(chosen);
			MinecraftClient.getInstance().execute(() -> whenChosen.accept(result));
		}, "roleplayers-quill-file-dialog");
		thread.setDaemon(true);
		thread.start();
	}

	/** Opens a folder in the system's file manager, for the "it was saved here" message. */
	public static void reveal(@Nullable Path path) {
		if (path == null) {
			return;
		}
		net.minecraft.util.Util.getOperatingSystem().open(path.toUri());
	}
}
