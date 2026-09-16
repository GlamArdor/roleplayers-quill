package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * One square of a shelf, drawn the same way wherever the shelf is.
 *
 * <p>Most characters are simply drawn in the middle of the square. The exception is what a resource
 * pack keeps in the private use area besides its icons: pictures for the interface – a panel, a
 * backdrop, a black rectangle the size of the screen – written as characters of no width so they
 * can be pinned anywhere a line of text goes. Drawing one of those in a grid seventeen pixels wide
 * covers the grid, the chat behind it and everything else.
 *
 * <p>So they are shown as an empty frame, and the picture itself is only drawn while the cursor is
 * on the square – shrunk to fit a box, which is enough to recognise it by and cannot cover anything.
 */
public final class SymbolCell {
	/** How large the shrunk-down look of an outsized character may be. */
	private static final int PREVIEW = 72;

	private SymbolCell() {
	}

	/** Draws the character, or the frame that stands in for one too large to draw here. */
	public static void draw(DrawContext context, TextRenderer textRenderer, String symbol,
			int x, int y, int cell) {
		if (!symbol.isEmpty() && Symbols.oversized(symbol.codePointAt(0))) {
			int inset = 3;
			context.drawBorder(x + inset, y + inset, cell - inset * 2, cell - inset * 2, 0xFF808080);
			return;
		}
		context.drawText(textRenderer, symbol,
				x + (cell - textRenderer.getWidth(symbol)) / 2, y + 5, 0xFFF0F0F0, false);
	}

	/**
	 * Draws the shrunk-down look of an outsized character, if that is what is under the cursor.
	 *
	 * <p>Called after the whole grid, so that it lies over the squares rather than under the ones
	 * drawn after it.
	 *
	 * @param x where to put the left edge of the box; it is placed below and right of the cursor
	 */
	public static void preview(DrawContext context, TextRenderer textRenderer, String symbol,
			int x, int y, int screenWidth, int screenHeight) {
		if (symbol.isEmpty() || !Symbols.oversized(symbol.codePointAt(0))) {
			return;
		}
		float[] size = Symbols.drawnSize(symbol.codePointAt(0));
		float scale = size == null || size[0] <= 0.0f || size[1] <= 0.0f
				? 1.0f
				: Math.min(PREVIEW / size[0], PREVIEW / size[1]);

		int boxX = Math.min(x, screenWidth - PREVIEW - 8);
		int boxY = Math.min(y, screenHeight - PREVIEW - 8);
		boxX = Math.max(4, boxX);
		boxY = Math.max(4, boxY);

		context.fill(boxX - 2, boxY - 2, boxX + PREVIEW + 2, boxY + PREVIEW + 2, 0xF0101010);
		context.drawBorder(boxX - 2, boxY - 2, PREVIEW + 4, PREVIEW + 4, 0xFF808080);

		// Clipped to the box as well as shrunk to it. A picture meant for the interface can be
		// anchored well away from where the character sits, and the whole promise here is that
		// looking at one of these cannot cover the screen.
		context.enableScissor(boxX, boxY, boxX + PREVIEW, boxY + PREVIEW);
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(boxX, boxY);
		context.getMatrices().scale(scale, scale);
		context.drawText(textRenderer, symbol, 0, 0, 0xFFFFFFFF, false);
		context.getMatrices().popMatrix();
		context.disableScissor();
	}
}
