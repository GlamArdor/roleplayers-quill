package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The toolbar's pictures, drawn rather than painted.
 *
 * <p>Every icon here is rectangles and letters put down with the game's own font, which means the
 * mod ships no texture atlas, nothing to keep in step with a resource pack, and nothing that goes
 * blurry when the editor is scaled up. It also means the bold button is drawn with a bold B, which
 * is about as clear as an icon can be.
 */
public final class Icons {
	/** Anything drawn here fits in a 16 by 16 box whose top left corner is the given point. */
	@FunctionalInterface
	public interface Icon {
		void draw(DrawContext context, int x, int y, int color);
	}

	private Icons() {
	}

	private static void glyph(DrawContext context, String text, Formatting style, int x, int y, int color) {
		var renderer = MinecraftClient.getInstance().textRenderer;
		Text label = style == null ? Text.literal(text) : Text.literal(text).formatted(style);
		int width = renderer.getWidth(label);
		context.drawText(renderer, label, x + (16 - width) / 2, y + 4, color, false);
	}

	private static void bar(DrawContext context, int x, int y, int width, int color) {
		context.fill(x, y, x + width, y + 1, color);
	}

	public static final Icon BOLD = (context, x, y, color) -> glyph(context, "B", Formatting.BOLD, x, y, color);
	public static final Icon ITALIC = (context, x, y, color) -> glyph(context, "I", Formatting.ITALIC, x, y, color);
	public static final Icon UNDERLINE = (context, x, y, color) -> glyph(context, "U", Formatting.UNDERLINE, x, y, color);
	public static final Icon STRIKE = (context, x, y, color) -> glyph(context, "S", Formatting.STRIKETHROUGH, x, y, color);
	public static final Icon OBFUSCATED = (context, x, y, color) -> glyph(context, "?", Formatting.OBFUSCATED, x, y, color);

	public static final Icon ALIGN_LEFT = (context, x, y, color) -> {
		for (int i = 0; i < 4; i++) {
			bar(context, x + 3, y + 4 + i * 3, i % 2 == 0 ? 10 : 7, color);
		}
	};

	public static final Icon ALIGN_CENTER = (context, x, y, color) -> {
		for (int i = 0; i < 4; i++) {
			int width = i % 2 == 0 ? 10 : 6;
			bar(context, x + 8 - width / 2, y + 4 + i * 3, width, color);
		}
	};

	public static final Icon ALIGN_RIGHT = (context, x, y, color) -> {
		for (int i = 0; i < 4; i++) {
			int width = i % 2 == 0 ? 10 : 7;
			bar(context, x + 13 - width, y + 4 + i * 3, width, color);
		}
	};

	public static final Icon ALIGN_JUSTIFY = (context, x, y, color) -> {
		for (int i = 0; i < 4; i++) {
			bar(context, x + 3, y + 4 + i * 3, 10, color);
		}
	};

	public static final Icon LIST_BULLET = (context, x, y, color) -> {
		for (int i = 0; i < 3; i++) {
			context.fill(x + 3, y + 4 + i * 4, x + 5, y + 6 + i * 4, color);
			bar(context, x + 7, y + 5 + i * 4, 6, color);
		}
	};

	/**
	 * Two numbered rows, not three.
	 *
	 * <p>A digit is eight pixels tall and the icon is sixteen, so three rows of them cannot fit and
	 * the third used to hang out of the bottom of the button.
	 */
	public static final Icon LIST_NUMBER = (context, x, y, color) -> {
		var renderer = MinecraftClient.getInstance().textRenderer;
		for (int i = 0; i < 2; i++) {
			context.drawText(renderer, String.valueOf(i + 1), x + 2, y + 1 + i * 8, color, false);
			bar(context, x + 9, y + 5 + i * 8, 5, color);
		}
	};

	/**
	 * Four lines of text with an arrow beside the two that move.
	 *
	 * <p>Drawn pixel by pixel rather than through the helpers below. Those put the arrow on top of
	 * the lines, and at sixteen pixels across an arrow on top of a line is not an arrow and a line,
	 * it is a smudge.
	 */
	public static final Icon INDENT_MORE = (context, x, y, color) -> {
		for (int row : new int[] {3, 7, 11}) {
			context.fill(x + 7, y + row, x + 14, y + row + 1, color);
		}
		context.fill(x + 2, y + 5, x + 3, y + 10, color);
		context.fill(x + 3, y + 6, x + 4, y + 9, color);
		context.fill(x + 4, y + 7, x + 5, y + 8, color);
	};

	public static final Icon INDENT_LESS = (context, x, y, color) -> {
		for (int row : new int[] {3, 7, 11}) {
			context.fill(x + 7, y + row, x + 14, y + row + 1, color);
		}
		context.fill(x + 4, y + 5, x + 5, y + 10, color);
		context.fill(x + 3, y + 6, x + 4, y + 9, color);
		context.fill(x + 2, y + 7, x + 3, y + 8, color);
	};

	private static void arrow(DrawContext context, int x, int y, int direction, int color) {
		for (int i = 0; i < 3; i++) {
			context.fill(x + i * direction, y - i, x + i * direction + 1, y + i + 1, color);
		}
	}

	/**
	 * A paintbrush held at an angle: a handle, a ferrule and bristles.
	 *
	 * <p>It was a straight stem with a cross-piece, which reads as a plus sign or a sword rather
	 * than as the thing every word processor draws here.
	 */
	public static final Icon BRUSH = (context, x, y, color) -> {
		// Upright, in four bands with a clear gap between them: handle, ferrule, head, point. At an
		// angle it would be truer to the one in a word processor and completely unreadable at
		// sixteen pixels – every diagonal comes out as a single grey wedge. What makes a brush
		// recognisable this small is the silhouette widening and then coming back to a point.
		context.fill(x + 6, y + 1, x + 10, y + 5, color);
		context.fill(x + 5, y + 6, x + 11, y + 8, color);
		context.fill(x + 4, y + 9, x + 12, y + 12, color);
		context.fill(x + 6, y + 12, x + 10, y + 14, color);
		context.fill(x + 7, y + 14, x + 9, y + 15, color);
	};

	/** A flourish between two rules: the dividers a book is decorated with. */
	public static final Icon ORNAMENT = (context, x, y, color) -> {
		context.fill(x + 2, y + 7, x + 6, y + 8, color);
		context.fill(x + 10, y + 7, x + 14, y + 8, color);
		context.fill(x + 7, y + 6, x + 9, y + 7, color);
		context.fill(x + 6, y + 7, x + 10, y + 9, color);
		context.fill(x + 7, y + 9, x + 9, y + 10, color);
	};

	public static final Icon LINK = (context, x, y, color) -> {
		context.drawBorder(x + 2, y + 5, 7, 6, color);
		context.drawBorder(x + 7, y + 5, 7, 6, color);
		context.fill(x + 6, y + 7, x + 10, y + 8, color);
	};

	public static final Icon SYMBOL = (context, x, y, color) -> glyph(context, "Ω", null, x, y, color);
	public static final Icon HYPHEN = (context, x, y, color) -> glyph(context, "a-", null, x, y, color);

	public static final Icon TABLE = (context, x, y, color) -> {
		context.drawBorder(x + 2, y + 4, 12, 9, color);
		context.fill(x + 2, y + 7, x + 14, y + 8, color);
		context.fill(x + 7, y + 4, x + 8, y + 13, color);
	};

	public static final Icon RULE = (context, x, y, color) -> {
		bar(context, x + 2, y + 5, 12, color);
		bar(context, x + 2, y + 10, 12, color);
	};

	public static final Icon VOICE = (context, x, y, color) -> {
		context.fill(x + 6, y + 2, x + 10, y + 9, color);
		context.fill(x + 4, y + 7, x + 5, y + 10, color);
		context.fill(x + 11, y + 7, x + 12, y + 10, color);
		context.fill(x + 5, y + 9, x + 11, y + 10, color);
		context.fill(x + 7, y + 10, x + 9, y + 13, color);
		context.fill(x + 4, y + 13, x + 12, y + 14, color);
	};

	/**
	 * An arrow going back, with a tail that turns under it.
	 *
	 * <p>Drawn out rather than built from the arrow helper below: that helper grows away from the
	 * point it is handed, so each head came out pointing the opposite way to the one asked for and
	 * undo and redo wore each other's arrows.
	 */
	public static final Icon UNDO = (context, x, y, color) -> {
		context.fill(x + 3, y + 7, x + 4, y + 8, color);
		context.fill(x + 4, y + 6, x + 5, y + 9, color);
		context.fill(x + 5, y + 5, x + 6, y + 10, color);
		context.fill(x + 5, y + 7, x + 12, y + 8, color);
		context.fill(x + 11, y + 7, x + 12, y + 12, color);
		context.fill(x + 7, y + 11, x + 12, y + 12, color);
	};

	public static final Icon REDO = (context, x, y, color) -> {
		context.fill(x + 12, y + 7, x + 13, y + 8, color);
		context.fill(x + 11, y + 6, x + 12, y + 9, color);
		context.fill(x + 10, y + 5, x + 11, y + 10, color);
		context.fill(x + 4, y + 7, x + 11, y + 8, color);
		context.fill(x + 4, y + 7, x + 5, y + 12, color);
		context.fill(x + 4, y + 11, x + 9, y + 12, color);
	};

	/**
	 * An arrow into a tray, and one out of it.
	 *
	 * <p>Drawn out rather than through the up and down helpers below: those build the head the
	 * wrong way round, so the arrow for bringing a book in flew upwards and the one for sending
	 * it out flew down.
	 */
	public static final Icon IMPORT = (context, x, y, color) -> {
		context.drawBorder(x + 2, y + 10, 12, 5, color);
		context.fill(x + 7, y + 2, x + 9, y + 8, color);
		context.fill(x + 4, y + 7, x + 12, y + 8, color);
		context.fill(x + 5, y + 8, x + 11, y + 9, color);
		context.fill(x + 6, y + 9, x + 10, y + 10, color);
		context.fill(x + 7, y + 10, x + 9, y + 11, color);
	};

	public static final Icon EXPORT = (context, x, y, color) -> {
		context.drawBorder(x + 2, y + 10, 12, 5, color);
		context.fill(x + 7, y + 5, x + 9, y + 11, color);
		context.fill(x + 4, y + 4, x + 12, y + 5, color);
		context.fill(x + 5, y + 3, x + 11, y + 4, color);
		context.fill(x + 6, y + 2, x + 10, y + 3, color);
		context.fill(x + 7, y + 1, x + 9, y + 2, color);
	};

	private static void down(DrawContext context, int x, int y, int color) {
		for (int i = 0; i < 4; i++) {
			context.fill(x - 3 + i, y - i, x + 4 - i, y - i + 1, color);
		}
	}

	private static void up(DrawContext context, int x, int y, int color) {
		for (int i = 0; i < 4; i++) {
			context.fill(x - 3 + i, y + i, x + 4 - i, y + i + 1, color);
		}
	}

	public static final Icon PAGE_ADD = (context, x, y, color) -> {
		context.drawBorder(x + 3, y + 2, 10, 12, color);
		context.fill(x + 7, y + 5, x + 9, y + 11, color);
		context.fill(x + 5, y + 7, x + 11, y + 9, color);
	};

	/** Two pages with a gap between them and a mark in the gap: a page wedged in where it was not. */
	public static final Icon PAGE_INSERT = (context, x, y, color) -> {
		context.drawBorder(x + 1, y + 2, 5, 12, color);
		context.drawBorder(x + 10, y + 2, 5, 12, color);
		context.fill(x + 7, y + 5, x + 9, y + 11, color);
		context.fill(x + 6, y + 7, x + 10, y + 9, color);
	};

	public static final Icon PAGE_REMOVE = (context, x, y, color) -> {
		context.drawBorder(x + 3, y + 2, 10, 12, color);
		for (int i = 0; i < 6; i++) {
			context.fill(x + 5 + i, y + 5 + i, x + 6 + i, y + 6 + i, color);
			context.fill(x + 10 - i, y + 5 + i, x + 11 - i, y + 6 + i, color);
		}
	};

	public static final Icon PAGE_COPY = (context, x, y, color) -> {
		context.drawBorder(x + 2, y + 2, 9, 10, color);
		context.drawBorder(x + 5, y + 5, 9, 10, color);
	};

	public static final Icon PAGE_PASTE = (context, x, y, color) -> {
		context.drawBorder(x + 3, y + 3, 10, 11, color);
		context.fill(x + 6, y + 1, x + 10, y + 4, color);
	};

	public static final Icon PAGES = (context, x, y, color) -> {
		context.drawBorder(x + 2, y + 3, 6, 9, color);
		context.drawBorder(x + 8, y + 3, 6, 9, color);
	};

	/** An icon that is simply a character, for the symbol bar. */
	public static Icon of(String symbol) {
		return (context, x, y, color) -> glyph(context, symbol, null, x, y, color);
	}

	/** Text spilling from one page onto the next: three bars, then an elbow and an arrow down. */
	public static final Icon REFLOW = (context, x, y, color) -> {
		bar(context, x + 2, y + 3, 9, color);
		bar(context, x + 2, y + 6, 9, color);
		bar(context, x + 2, y + 9, 5, color);
		context.fill(x + 11, y + 3, x + 12, y + 10, color);
		for (int i = 0; i < 3; i++) {
			context.fill(x + 9 + i, y + 10 + i, x + 14 - i, y + 11 + i, color);
		}
	};

	public static final Icon PAGE_LEFT = (context, x, y, color) -> triangle(context, x + 10, y + 8, -1, color);
	public static final Icon PAGE_RIGHT = (context, x, y, color) -> triangle(context, x + 5, y + 8, 1, color);

	private static void triangle(DrawContext context, int x, int y, int direction, int color) {
		for (int i = 0; i < 5; i++) {
			context.fill(x + i * direction, y - 4 + i, x + i * direction + 1, y + 5 - i, color);
		}
	}

	/** A floppy disk, which still means "save" to everyone who has never held one. */
	public static final Icon SAVE = (context, x, y, color) -> {
		context.drawBorder(x + 2, y + 2, 12, 12, color);
		context.fill(x + 5, y + 3, x + 11, y + 7, color);
		context.fill(x + 7, y + 3, x + 9, y + 6, 0xFF202020);
		context.drawBorder(x + 4, y + 9, 8, 5, color);
	};

	/** Two stacks of lines side by side: a page in two columns. */
	public static final Icon COLUMNS = (context, x, y, color) -> {
		for (int i = 0; i < 4; i++) {
			int row = y + 3 + i * 3;
			context.fill(x + 3, row, x + 7, row + 1, color);
			context.fill(x + 9, row, x + 13, row + 1, color);
		}
	};

	/** A word, a run of dots, a figure: a leader as it appears in a price list. */
	public static final Icon LEADER = (context, x, y, color) -> {
		context.fill(x + 2, y + 6, x + 5, y + 9, color);
		for (int i = 0; i < 3; i++) {
			context.fill(x + 6 + i * 2, y + 8, x + 7 + i * 2, y + 9, color);
		}
		context.fill(x + 12, y + 6, x + 14, y + 9, color);
	};

	/** A page with a small raised figure by it: a footnote. */
	public static final Icon FOOTNOTE = (context, x, y, color) -> {
		context.fill(x + 3, y + 4, x + 10, y + 5, color);
		context.fill(x + 3, y + 7, x + 10, y + 8, color);
		context.fill(x + 3, y + 10, x + 8, y + 11, color);
		context.fill(x + 11, y + 3, x + 13, y + 4, color);
	};

	/** Lines with numbers beside them: a contents page. */
	public static final Icon CONTENTS = (context, x, y, color) -> {
		for (int i = 0; i < 3; i++) {
			int row = y + 4 + i * 3;
			context.fill(x + 3, row, x + 9, row + 1, color);
			context.fill(x + 11, row, x + 13, row + 1, color);
		}
	};

	/** A page with a corner turned: something already written on. */
	public static final Icon TEMPLATE = (context, x, y, color) -> {
		context.drawBorder(x + 3, y + 2, 10, 12, color);
		context.fill(x + 5, y + 5, x + 11, y + 6, color);
		context.fill(x + 5, y + 8, x + 11, y + 9, color);
		context.fill(x + 5, y + 11, x + 9, y + 12, color);
	};

	/** A heading over two lines of text: a paragraph with a look of its own. */
	public static final Icon PARAGRAPH = (context, x, y, color) -> {
		context.fill(x + 4, y + 3, x + 12, y + 5, color);
		context.fill(x + 3, y + 7, x + 13, y + 8, color);
		context.fill(x + 3, y + 10, x + 13, y + 11, color);
	};

	/** A quotation mark turning into an angled one: what correcting as you type mostly does. */
	public static final Icon CORRECT = (context, x, y, color) -> {
		context.fill(x + 3, y + 4, x + 5, y + 8, color);
		context.fill(x + 6, y + 4, x + 8, y + 8, color);
		context.fill(x + 10, y + 6, x + 13, y + 7, color);
		context.fill(x + 9, y + 9, x + 13, y + 10, color);
	};

	/** A magnifying glass: a ring and a handle off its corner. */
	public static final Icon FIND = (context, x, y, color) -> {
		context.drawBorder(x + 3, y + 3, 8, 8, color);
		context.fill(x + 10, y + 10, x + 13, y + 13, color);
	};

	public static final Icon CLEAR = (context, x, y, color) -> glyph(context, "×", null, x, y, color);
	public static final Icon COLOR_SWATCH = (context, x, y, color) -> {
		context.drawBorder(x + 2, y + 2, 12, 12, 0xFF000000);
		context.fill(x + 3, y + 3, x + 13, y + 13, color);
	};
}
