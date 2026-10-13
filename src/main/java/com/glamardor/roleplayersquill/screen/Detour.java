package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Opens one of this mod's windows over a screen that is coming straight back.
 *
 * <p>The game tells a screen it is being replaced, and some screens take that as the end. A sign
 * editor sends its text to the server the moment it is replaced, and the server, having had the
 * text, stops letting the player edit that sign – so anything put in after a trip to the symbols
 * was thrown away when the sign was finished. A screen that cares asks {@link #isLeaving} and,
 * when the answer is yes, waits for the real close instead.
 */
public final class Detour {
	@Nullable
	private static Screen leaving;

	private Detour() {
	}

	/** Shows {@code window} in place of {@code owner}, which it is expected to return to. */
	public static void open(Screen owner, Screen window) {
		leaving = owner;
		try {
			MinecraftClient.getInstance().setScreen(window);
		} finally {
			leaving = null;
		}
	}

	/** Whether {@code screen} is being put aside for a moment rather than closed. */
	public static boolean isLeaving(Screen screen) {
		return leaving == screen;
	}
}
