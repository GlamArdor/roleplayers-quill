package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.text.Spelling;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * The dotted red line under a word nothing recognises, drawn under any text at all.
 *
 * <p>The book draws its own, because a page is laid out by this mod and knows to the pixel where
 * every character sits. Everywhere else – the chat box, a sign – the text belongs to the game, and
 * all this needs is a way of asking where a character is. Hence the little function handed in: the
 * chat box answers it out of its own scrolling, a sign out of the width of what is in front of it.
 */
public final class SpellMarks {
	private static final int INK = 0xFFC03030;

	private SpellMarks() {
	}

	/** Whether anything outside a book should be marked at all. */
	public static boolean wanted() {
		QuillConfig config = QuillConfig.get();
		return config.spellCheck && config.spellElsewhere && Spelling.ready();
	}

	/**
	 * Marks every unrecognised word of this text.
	 *
	 * @param xOf   where a character sits across the screen, by its index in the text
	 * @param y     the top of the line the text is drawn on
	 * @param left  the first pixel that may be drawn on, for text that scrolls out of its box
	 * @param right one past the last
	 */
	public static void draw(DrawContext context, String text, IntUnaryOperator xOf, int y,
			int left, int right) {
		if (!wanted()) {
			return;
		}
		List<Spelling.Word> words = Spelling.unknownIn(text);
		for (Spelling.Word word : words) {
			int from = Math.max(left, xOf.applyAsInt(word.from()));
			int to = Math.min(right, xOf.applyAsInt(word.to()));
			for (int x = from; x < to; x += 2) {
				context.fill(x, y + 8, x + 1, y + 9, INK);
			}
		}
	}

	/** The word under a point, or null when the point is not on one. */
	public static Spelling.Word at(String text, IntUnaryOperator xOf, double mouseX) {
		if (!wanted()) {
			return null;
		}
		for (Spelling.Word word : Spelling.unknownIn(text)) {
			if (mouseX >= xOf.applyAsInt(word.from()) && mouseX <= xOf.applyAsInt(word.to())) {
				return word;
			}
		}
		return null;
	}
}
