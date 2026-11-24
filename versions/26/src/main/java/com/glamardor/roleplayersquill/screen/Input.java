package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.input.MouseButtonEvent;

/**
 * The bridge between the game's input events and the numbers the editor was written against.
 *
 * <p>From 26.3 the game reads the mouse through SDL, which counts its buttons from one (left 1,
 * middle 2, right 3) where GLFW counted from nought (left 0, right 1, middle 2). Everything in the
 * mod that asks "which button" asks in GLFW's numbers, so the event is turned into those once,
 * at the door.
 */
public final class Input {
	public static final int LEFT = 0;
	public static final int RIGHT = 1;
	public static final int MIDDLE = 2;

	private Input() {
	}

	public static int button(MouseButtonEvent event) {
		return switch (event.button()) {
			case 1 -> LEFT;
			case 3 -> RIGHT;
			case 2 -> MIDDLE;
			default -> event.button() - 1;
		};
	}
}
