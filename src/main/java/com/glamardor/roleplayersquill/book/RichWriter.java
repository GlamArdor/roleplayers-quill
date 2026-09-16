package com.glamardor.roleplayersquill.book;

import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillStyle;
import com.glamardor.roleplayersquill.text.Widths;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The other way a page can be written: as a text component rather than a string.
 *
 * <p>A written book stores its pages as components, and the book renderer resolves clicks and
 * tooltips on them – that is not something this mod adds, it is how the game has always worked. The
 * catch is that the client cannot normally hand the server one: signing a book sends strings, and
 * the server builds the components itself.
 *
 * <p>Creative mode is the exception. A creative player's client is allowed to put a finished item
 * into its own inventory – that is what picking an item out of the creative menu is – and the
 * server takes the item as given, components and all. So a book written this way needs creative,
 * and nothing else: no permission, no plugin, no command. Off a creative server it falls back to
 * {@code §} codes, which is what {@link com.glamardor.roleplayersquill.text.LegacyCodec} is for.
 *
 * <p>The layout is identical either way. Alignment is still spaces, because a component has no way
 * of saying "centre this" – what the components buy is real colours, links, tooltips and a page
 * limit of 32767 characters instead of 1024.
 */
public final class RichWriter {
	private RichWriter() {
	}

	/** One page, laid out and written as a component tree. */
	public static Text encode(List<Paragraph> page, List<Layout.LaidLine> lines) {
		MutableText out = Text.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				out.append(Text.literal("\n"));
			}
			encodeLine(out, page, lines.get(i));
		}
		return out;
	}

	private static void encodeLine(MutableText out, List<Paragraph> page, Layout.LaidLine line) {
		Paragraph paragraph = page.get(line.paragraph);

		if (line.frame.present()) {
			out.append(Text.literal(String.valueOf(line.frame.bar)));
			appendSpaces(out, QuillStyle.PLAIN, Widths.pad(com.glamardor.roleplayersquill.text.FrameStyle.LEFT_MARGIN));
		}
		if (!line.leftPad.isEmpty()) {
			appendSpaces(out, QuillStyle.PLAIN, line.leftPad);
		}
		if (!line.marker.isEmpty()) {
			out.append(Text.literal(line.marker).setStyle(line.markerStyle.toRichVanilla(0)));
			if (!line.markerPad.isEmpty()) {
				appendSpaces(out, QuillStyle.PLAIN, line.markerPad);
			}
		}

		StringBuilder run = new StringBuilder();
		QuillStyle runStyle = null;
		for (int i = line.start; i < line.contentEnd; i++) {
			char c = paragraph.charAt(i);
			QuillStyle style = paragraph.styleAt(i);

			if (i == line.leaderAt) {
				flush(out, run, runStyle);
				runStyle = null;
				if (!line.leaderPad.isEmpty()) {
					appendSpaces(out, style.withObfuscated(false), line.leaderPad);
				}
				if (line.leaderDots > 0) {
					out.append(Text.literal(".".repeat(line.leaderDots)).setStyle(style.toRichVanilla(0)));
				}
				continue;
			}

			Widths.Padding pad = c == ' ' ? line.padFor(i) : null;
			if (pad != null && (pad.count() != 1 || pad.bold() != 0)) {
				flush(out, run, runStyle);
				runStyle = null;
				appendSpaces(out, style.withObfuscated(false), pad);
				continue;
			}

			if (runStyle == null || !runStyle.equals(style)) {
				flush(out, run, runStyle);
				runStyle = style;
			}
			run.append(c);
		}
		flush(out, run, runStyle);

		if (line.hyphen) {
			QuillStyle style = paragraph.styleAt(Math.max(line.start, line.contentEnd - 1));
			out.append(Text.literal("-").setStyle(style.withoutInteraction().toRichVanilla(0)));
		}

		if (line.frame.present()) {
			float used = line.frame.textLeft() + line.leftPad.width() + line.naturalWidth;
			appendSpaces(out, QuillStyle.PLAIN, Widths.pad(line.frame.barRight() - used));
			out.append(Text.literal(String.valueOf(line.frame.bar)));
		}
	}

	private static void appendSpaces(MutableText out, QuillStyle base, Widths.Padding padding) {
		int plain = Math.max(0, padding.count() - padding.bold());
		QuillStyle flat = base.withObfuscated(false).withBold(false).withoutInteraction();
		if (plain > 0) {
			out.append(Text.literal(" ".repeat(plain)).setStyle(flat.toRichVanilla(0)));
		}
		if (padding.bold() > 0) {
			out.append(Text.literal(" ".repeat(padding.bold())).setStyle(flat.withBold(true).toRichVanilla(0)));
		}
	}

	private static void flush(MutableText out, StringBuilder run, QuillStyle style) {
		if (run.isEmpty()) {
			return;
		}
		out.append(Text.literal(run.toString()).setStyle(style == null
				? QuillStyle.PLAIN.toRichVanilla(0)
				: style.toRichVanilla(0)));
		run.setLength(0);
	}
}
