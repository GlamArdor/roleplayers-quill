package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillStyle;
import com.glamardor.roleplayersquill.text.Widths;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Drawing a laid-out line onto a strip of parchment, for the windows that show what a page will
 * look like before anything is put on it.
 *
 * <p>The same line the editor draws and the encoder writes – leaders, list markers, padding and all
 * – because a preview that is drawn by different code is a preview of a different page.
 */
public final class BookPreview {
	private static final int INK = 0xFF000000;

	private BookPreview() {
	}

	/** Draws one laid-out line with its left edge at x. */
	public static void drawLine(DrawContext context, TextRenderer textRenderer, Layout.LaidLine line,
			java.util.List<Paragraph> page, int x, int y) {
		Paragraph paragraph = page.get(line.paragraph);
		float at = x + line.leftPad.width();
		if (line.frame.present()) {
			String bar = String.valueOf(line.frame.bar);
			context.drawText(textRenderer, bar, x, y, INK, false);
			context.drawText(textRenderer, bar, (int) (x + line.frame.barRight()), y, INK, false);
			at += line.frame.textLeft();
		}

		if (!line.marker.isEmpty()) {
			context.drawText(textRenderer,
					Text.literal(line.marker).setStyle(line.markerStyle.toVanilla(0)), (int) at, y, INK, false);
			at += Widths.widthOf(line.marker, line.markerStyle.bold()) + line.markerPad.width();
		}

		StringBuilder run = new StringBuilder();
		QuillStyle runStyle = null;
		for (int i = line.start; i < line.contentEnd; i++) {
			QuillStyle style = paragraph.styleAt(i);
			if (i == line.leaderAt) {
				at = flush(context, textRenderer, run, runStyle, at, y);
				runStyle = null;
				at += line.leaderPad.width();
				if (line.leaderDots > 0) {
					String dots = ".".repeat(line.leaderDots);
					context.drawText(textRenderer, Text.literal(dots).setStyle(style.toVanilla(0)),
							(int) at, y, INK, false);
					at += Widths.widthOf(dots, style.bold());
				}
				continue;
			}
			Widths.Padding pad = paragraph.charAt(i) == ' ' ? line.padFor(i) : null;
			boolean widened = pad != null && (pad.count() != 1 || pad.bold() != 0);
			if (runStyle == null || !runStyle.equals(style) || widened) {
				at = flush(context, textRenderer, run, runStyle, at, y);
				runStyle = widened ? null : style;
			}
			if (widened) {
				at += pad.width();
				continue;
			}
			// The blank that holds two words together is drawn as the space it will be: the font has
			// no glyph of its own for it, and what has no glyph is drawn as a missing-glyph box.
			char c = paragraph.charAt(i);
			run.append(c == Widths.NOBREAK ? ' ' : c);
		}
		at = flush(context, textRenderer, run, runStyle, at, y);

		if (line.hyphen) {
			QuillStyle style = paragraph.styleAt(Math.max(line.start, line.contentEnd - 1));
			context.drawText(textRenderer, Text.literal("-").setStyle(style.toVanilla(0)), (int) at, y, INK, false);
		}
	}

	/**
	 * How far along the line a character of the paragraph sits, in pixels from the line's left edge.
	 *
	 * <p>Walked exactly as {@link #drawLine} walks it – the padding, the marker, the leader and the
	 * widened gaps of a justified line all move the text along, and a mark drawn under it that
	 * counted only the letters would sit under the wrong word on precisely the lines that are laid
	 * out most carefully.
	 *
	 * @param index a character index into the paragraph, clamped to the line it is asked about
	 */
	public static float offsetOf(Layout.LaidLine line, java.util.List<Paragraph> page, int index) {
		Paragraph paragraph = page.get(line.paragraph);
		float at = line.leftPad.width();
		if (line.frame.present()) {
			at += line.frame.textLeft();
		}
		if (!line.marker.isEmpty()) {
			at += Widths.widthOf(line.marker, line.markerStyle.bold()) + line.markerPad.width();
		}
		for (int i = line.start; i < Math.min(index, line.contentEnd); i++) {
			if (i == line.leaderAt) {
				at += line.leaderPad.width();
				if (line.leaderDots > 0) {
					at += Widths.widthOf(".".repeat(line.leaderDots), paragraph.styleAt(i).bold());
				}
				continue;
			}
			Widths.Padding pad = paragraph.charAt(i) == ' ' ? line.padFor(i) : null;
			if (pad != null && (pad.count() != 1 || pad.bold() != 0)) {
				at += pad.width();
				continue;
			}
			at += Widths.advance(paragraph.charAt(i), paragraph.styleAt(i).bold());
		}
		return at;
	}

	private static float flush(DrawContext context, TextRenderer textRenderer, StringBuilder run,
			@Nullable QuillStyle style, float x, int y) {
		if (run.isEmpty()) {
			return x;
		}
		QuillStyle applied = style == null ? QuillStyle.PLAIN : style;
		int colour = applied.color() == QuillStyle.INHERIT ? INK : 0xFF000000 | applied.color();
		String text = run.toString();
		context.drawText(textRenderer, Text.literal(text).setStyle(applied.toVanilla(colour & 0xFFFFFF)),
				(int) x, y, colour, false);
		run.setLength(0);
		return x + Widths.widthOf(text, applied.bold());
	}
}
