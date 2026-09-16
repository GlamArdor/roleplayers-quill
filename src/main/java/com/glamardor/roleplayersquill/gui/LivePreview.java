package com.glamardor.roleplayersquill.gui;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.config.QuillConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes the Cloth settings screen take effect while it is still open.
 *
 * <p>Cloth hands a value over only when the player presses save, which is right for a form and
 * wrong here: half of these settings are about how a page is laid out, and the editor is sitting
 * behind the menu showing exactly that. So the widgets are read every tick and written into the
 * live config, and the page relays itself as the sliders move.
 *
 * <p>The promise Cloth makes – that escape throws your edits away – is kept by copying the config
 * when the screen opens and copying it back if it closes without a save.
 */
public final class LivePreview {
	/**
	 * How long another screen may sit on top before the preview gives up.
	 *
	 * <p>Cloth puts a confirmation dialog in front of its own screen when you leave with unsaved
	 * changes, and reverting the moment that appears would undo the very thing being asked about.
	 */
	private static final long AWAY_MILLIS = 3000L;

	@Nullable
	private static Screen owner;
	@Nullable
	private static QuillConfig snapshot;
	private static List<Runnable> appliers = List.of();
	private static boolean saved;
	private static long awaySince;

	private LivePreview() {
	}

	public static void start(Screen screen, List<Runnable> widgets) {
		finish();
		owner = screen;
		appliers = List.copyOf(widgets);
		saved = false;
		awaySince = 0L;
		snapshot = copyOf(QuillConfig.get());
	}

	public static void markSaved() {
		saved = true;
		snapshot = copyOf(QuillConfig.get());
	}

	public static void tick(MinecraftClient client) {
		if (owner == null) {
			return;
		}
		if (client.currentScreen == owner) {
			awaySince = 0L;
			for (Runnable applier : appliers) {
				try {
					applier.run();
				} catch (Throwable error) {
					RoleplayersQuill.LOGGER.warn("A settings widget could not be previewed", error);
				}
			}
			return;
		}
		long now = System.currentTimeMillis();
		if (awaySince == 0L) {
			awaySince = now;
			return;
		}
		if (client.currentScreen == null || now - awaySince > AWAY_MILLIS) {
			finish();
		}
	}

	private static void finish() {
		if (owner != null && !saved && snapshot != null) {
			restore(snapshot, QuillConfig.get());
		}
		owner = null;
		snapshot = null;
		appliers = List.of();
		saved = false;
		awaySince = 0L;
	}

	/** True while our own settings screen is the one on show, which is what the blur mixin asks. */
	public static boolean isOurScreenOpen() {
		MinecraftClient client = MinecraftClient.getInstance();
		Screen screen = client.currentScreen;
		return screen != null && (screen == owner || screen instanceof FallbackConfigScreen);
	}

	private static QuillConfig copyOf(QuillConfig source) {
		QuillConfig copy = new QuillConfig();
		restore(source, copy);
		return copy;
	}

	private static void restore(QuillConfig from, QuillConfig to) {
		for (Field field : QuillConfig.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				Object value = field.get(from);
				// The lists are the player's own; copy them rather than sharing one instance, or the
				// snapshot would follow every edit and have nothing to revert to.
				if (value instanceof List<?> list) {
					value = new ArrayList<>(list);
				}
				field.set(to, value);
			} catch (IllegalAccessException error) {
				RoleplayersQuill.LOGGER.warn("Could not copy {} for the preview", field.getName(), error);
			}
		}
	}
}
