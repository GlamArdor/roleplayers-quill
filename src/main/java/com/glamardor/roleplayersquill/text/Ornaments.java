package com.glamardor.roleplayersquill.text;

import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Dividers: the ornaments a roleplay book is full of and nobody wants to build by hand.
 *
 * <p>Each one is built to the width of the page rather than typed out, so it comes out the same
 * length whatever the font does with the characters in it – and all of them are ordinary text, so a
 * reader without the mod sees the same ornament.
 */
public final class Ornaments {
	/**
	 * A divider, described rather than spelled out.
	 *
	 * @param left   what stands at each end, mirrored
	 * @param fill   the character the arms are made of, repeated as far as there is room
	 * @param centre what sits in the middle, with a space either side of it
	 */
	/**
	 * A divider: a rule across the page with something set into it, or a flourish on its own.
	 *
	 * <p>The rule is underlined blanks rather than a run of {@code ─}. Minecraft's font draws box
	 * drawing with daylight between the characters, so a line built that way comes out as a row of
	 * dashes; an underline is solid and can be made any width at all.
	 */
	public enum Divider {
		/** A plain rule, the full width of the page. */
		RULE("", "", false),
		/** A rule with a diamond set in the middle of it. */
		DIAMOND("", "❖", false),
		/** A rule with a flourish at each end. */
		FLOWER("❦", "", false),
		/** Two rules with a star between them: for a decree or a proclamation. */
		STAR("", "★", true),
		/** Beads at the ends of the string. */
		BEADS("•", "", false),
		/** Three sparks and nothing else. */
		SPARKS(null, "✦   ·   ✦   ·   ✦", false),
		/** A quiet break inside a chapter. */
		DOTS(null, "·   ·   ·", false),
		/** The asterism: the oldest way of saying "and now something else". */
		ASTERISM(null, "⁂", false);

		/** What stands at each end, or null for a flourish on its own with no rule at all. */
		private final String cap;
		private final String centre;
		private final boolean twoLines;

		Divider(String cap, String centre, boolean twoLines) {
			this.cap = cap;
			this.centre = centre;
			this.twoLines = twoLines;
		}

		public Text label() {
			return Text.translatable("roleplayersquill.ornament." + name().toLowerCase(Locale.ROOT));
		}

		/** The divider as a paragraph, ready to drop into a page. */
		public Paragraph build() {
			if (cap == null) {
				Paragraph out = new Paragraph(centre, QuillStyle.PLAIN);
				out.setAlignment(Alignment.CENTER);
				return out;
			}

			QuillStyle ruled = QuillStyle.PLAIN.withUnderlined(true).withStrikethrough(twoLines);
			float caps = Widths.widthOf(cap, false) * 2;
			float middle = centre.isEmpty() ? 0.0f
					: Widths.widthOf(centre, false) + 2 * Widths.space();
			// Half the rule each side, so the flourish sits in the middle of the page rather than in
			// the middle of whatever was left over.
			Widths.Padding arm = Widths.pad((Layout.PAGE_WIDTH - caps - middle) / 2.0f);

			Paragraph out = new Paragraph();
			if (!cap.isEmpty()) {
				out.insert(out.length(), cap, QuillStyle.PLAIN);
			}
			blanks(out, arm, ruled);
			if (!centre.isEmpty()) {
				out.insert(out.length(), " " + centre + " ", QuillStyle.PLAIN);
			}
			blanks(out, arm, ruled);
			if (!cap.isEmpty()) {
				out.insert(out.length(), cap, QuillStyle.PLAIN);
			}
			return out;
		}
	}

	/** Blanks with a line drawn on them, which is how this font draws a solid rule. */
	private static void blanks(Paragraph out, Widths.Padding padding, QuillStyle style) {
		int plain = padding.count() - padding.bold();
		if (plain > 0) {
			out.insert(out.length(), " ".repeat(plain), style);
		}
		if (padding.bold() > 0) {
			out.insert(out.length(), " ".repeat(padding.bold()), style.withBold(true));
		}
	}

	private Ornaments() {
	}

	/**
	 * Puts a frame round a page, or takes one off.
	 *
	 * <p>The frame is not written into the text: every paragraph is marked as being inside one, and
	 * the bars are put on each line as it is laid out. Which means the page can still be typed in,
	 * the text still wraps, and the frame follows it – see {@link FrameStyle}.
	 *
	 * @return the page, with a top and bottom edge added or removed
	 */
	public static List<Paragraph> frame(List<Paragraph> page, FrameStyle style) {
		List<Paragraph> out = new java.util.ArrayList<>();
		for (Paragraph paragraph : page) {
			// Whatever edges were there come off first, so that changing the style does not stack.
			if (FrameStyle.isEdge(paragraph)) {
				continue;
			}
			paragraph.setFrame(style);
			out.add(paragraph);
		}
		if (!style.present()) {
			return out;
		}
		out.add(0, style.edge(true));
		out.add(style.edge(false));
		return out;
	}
}
