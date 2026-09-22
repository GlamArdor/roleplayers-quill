package com.glamardor.roleplayersquill.reader;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * What a page looks like once the game has wrapped it: the codepoint and the style of every
 * character on every line.
 *
 * <p>Built from the very {@code List<OrderedText>} {@code BookScreen} itself draws from, so a
 * coordinate in here always agrees with a pixel on the screen – there is no second wrap of the same
 * text that could disagree with the one the eye is looking at. Selection, the search highlight and
 * copying are all built on this one parsing, each reading it a different way.
 */
public final class PageText {

	/** One wrapped line, in drawing order. */
	public record Line(int[] codePoints, Style[] styles) {
		public int length() {
			return codePoints.length;
		}

		public String text() {
			StringBuilder out = new StringBuilder(codePoints.length);
			for (int codePoint : codePoints) {
				out.appendCodePoint(codePoint);
			}
			return out.toString();
		}
	}

	private final List<Line> lines;

	private PageText(List<Line> lines) {
		this.lines = lines;
	}

	public static PageText of(List<OrderedText> wrapped) {
		List<Line> lines = new ArrayList<>(wrapped.size());
		for (OrderedText line : wrapped) {
			List<Integer> codePoints = new ArrayList<>();
			List<Style> styles = new ArrayList<>();
			line.accept((index, style, codePoint) -> {
				codePoints.add(codePoint);
				styles.add(style);
				return true;
			});
			int[] cps = new int[codePoints.size()];
			Style[] sts = new Style[styles.size()];
			for (int i = 0; i < cps.length; i++) {
				cps[i] = codePoints.get(i);
				sts[i] = styles.get(i);
			}
			lines.add(new Line(cps, sts));
		}
		return new PageText(lines);
	}

	public int lineCount() {
		return lines.size();
	}

	public Line line(int index) {
		return lines.get(index);
	}

	/** The pixel width, from the line's own left edge, of everything before {@code column}. */
	public int widthTo(TextRenderer renderer, int lineIndex, int column) {
		Line line = lines.get(lineIndex);
		return renderer.getWidth(slice(line, 0, Math.min(column, line.length())));
	}

	/** The column whose caret sits closest to {@code localX} pixels from the line's left edge. */
	public int columnAt(TextRenderer renderer, int lineIndex, int localX) {
		Line line = lines.get(lineIndex);
		int width = 0;
		for (int i = 0; i < line.length(); i++) {
			int charWidth = renderer.getWidth(slice(line, i, i + 1));
			if (localX < width + charWidth / 2) {
				return i;
			}
			width += charWidth;
		}
		return line.length();
	}

	/** The text a selection from one point to another would copy: one line join per line crossed. */
	public String textBetween(int fromLine, int fromColumn, int toLine, int toColumn) {
		StringBuilder out = new StringBuilder();
		for (int i = fromLine; i <= toLine; i++) {
			Line line = lines.get(i);
			int from = i == fromLine ? Math.min(fromColumn, line.length()) : 0;
			int to = i == toLine ? Math.min(toColumn, line.length()) : line.length();
			out.append(line.text(), from, to);
			if (i < toLine) {
				out.append('\n');
			}
		}
		return out.toString();
	}

	private static OrderedText slice(Line line, int from, int to) {
		return visitor -> {
			for (int i = from; i < to; i++) {
				if (!visitor.accept(i - from, line.styles()[i], line.codePoints()[i])) {
					return false;
				}
			}
			return true;
		};
	}
}
