package com.glamardor.roleplayersquill.book;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * The system's own open and save dialogs.
 *
 * <p>From 26.3 the game runs on SDL rather than GLFW and no longer carries tinyfd, so the dialogs
 * are SDL's. SDL's are asynchronous already: they are asked for on the render thread, which is
 * where SDL wants them asked for, the game keeps drawing while the window is up, and the answer
 * arrives through a callback while the game pumps its events. The callback may come on another
 * thread, so the answer is handed back on the client thread.
 *
 * <p>Everything the dialog reads – the filters, the strings, the callback itself – has to stay alive
 * until the answer comes, so it is all allocated by hand and freed in the callback.
 */
public final class FileDialogs {
	private static final int OPEN_FILE = SDLDialog.SDL_FILEDIALOG_OPENFILE;
	private static final int SAVE_FILE = SDLDialog.SDL_FILEDIALOG_SAVEFILE;

	private FileDialogs() {
	}

	/** Asks for a file to read. The callback runs on the client thread, with null for a cancel. */
	public static void open(String title, String[] patterns, String description, Consumer<Path> whenChosen) {
		show(OPEN_FILE, title, defaultPath(), patterns, description, whenChosen);
	}

	/** Asks where to write. */
	public static void save(String title, String suggestedName, String[] patterns, String description,
			Consumer<Path> whenChosen) {
		show(SAVE_FILE, title, BookIO.exportDir().resolve(suggestedName).toString(), patterns, description, whenChosen);
	}

	private static String defaultPath() {
		return BookIO.exportDir().toString() + java.io.File.separator;
	}

	private static void show(int type, String title, String location, String[] patterns, String description,
			Consumer<Path> whenChosen) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.isSameThread()) {
			minecraft.execute(() -> show(type, title, location, patterns, description, whenChosen));
			return;
		}
		List<ByteBuffer> strings = new ArrayList<>();
		SDL_DialogFileFilter.Buffer filters = null;
		SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
		int properties = 0;
		try {
			// SDL wants "txt;md", not "*.txt" and "*.md".
			StringBuilder joined = new StringBuilder();
			for (String pattern : patterns) {
				String bare = pattern.startsWith("*.") ? pattern.substring(2) : pattern;
				if (bare.equals("*")) {
					continue;
				}
				if (!joined.isEmpty()) {
					joined.append(';');
				}
				joined.append(bare);
			}
			if (!joined.isEmpty()) {
				filters = SDL_DialogFileFilter.calloc(1);
				filters.get(0).set(keep(strings, description), keep(strings, joined.toString()));
			}
			SDL_DialogFileFilter.Buffer heldFilters = filters;
			callback[0] = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
				Path result = null;
				try {
					if (filelist == 0L) {
						RoleplayersQuill.LOGGER.warn("The file dialog could not be opened");
					} else {
						long first = MemoryUtil.memGetAddress(filelist);
						String chosen = first == 0L ? null : MemoryUtil.memUTF8(first);
						result = chosen == null || chosen.isBlank() ? null : Path.of(chosen);
					}
				} catch (Throwable error) {
					RoleplayersQuill.LOGGER.warn("The file dialog answered with something unreadable", error);
				} finally {
					Path answer = result;
					minecraft.execute(() -> {
						for (ByteBuffer string : strings) {
							MemoryUtil.memFree(string);
						}
						if (heldFilters != null) {
							heldFilters.free();
						}
						callback[0].free();
						whenChosen.accept(answer);
					});
				}
			});

			properties = SDLProperties.SDL_CreateProperties();
			SDLProperties.SDL_SetStringProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title);
			SDLProperties.SDL_SetStringProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, location);
			SDLProperties.SDL_SetPointerProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER,
					minecraft.getWindow().handle());
			if (filters != null) {
				SDLProperties.SDL_SetPointerProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER,
						filters.address());
				SDLProperties.SDL_SetNumberProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1);
			}
			SDLDialog.SDL_ShowFileDialogWithProperties(type, callback[0], 0L, properties);
		} catch (Throwable error) {
			// No dialog on this system: not a reason to lose the book.
			RoleplayersQuill.LOGGER.warn("The file dialog could not be opened", error);
			for (ByteBuffer string : strings) {
				MemoryUtil.memFree(string);
			}
			if (filters != null) {
				filters.free();
			}
			if (callback[0] != null) {
				callback[0].free();
			}
			whenChosen.accept(null);
		} finally {
			// SDL copies what it needs out of the properties before returning.
			if (properties != 0) {
				SDLProperties.SDL_DestroyProperties(properties);
			}
		}
	}

	private static ByteBuffer keep(List<ByteBuffer> strings, String text) {
		ByteBuffer buffer = MemoryUtil.memUTF8(text);
		strings.add(buffer);
		return buffer;
	}

	/** Opens a folder in the system's file manager, for the "it was saved here" message. */
	public static void reveal(@Nullable Path path) {
		if (path == null) {
			return;
		}
		com.mojang.blaze3d.Blaze3D.openPath(path);
	}
}
