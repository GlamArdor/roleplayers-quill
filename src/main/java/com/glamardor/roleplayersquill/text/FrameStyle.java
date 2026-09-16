package com.glamardor.roleplayersquill.text;

import net.minecraft.text.Text;

import java.util.Locale;

/**
 * A border drawn round the lines of a page.
 *
 * <p>Not a picture laid over the page: the bars are written into every line as it is laid out, so
 * the text inside stays text and can be typed, deleted and reflowed with the frame following it
 * along. That is the whole difference between a frame worth having and a frame that has to be torn
 * off before anything can be written inside it.
 *
 * <h2>Why the top and bottom are not box drawing</h2>
 *
 * <p>Because Minecraft's font does not join them up. A run of {@code ═} comes out as a row of
 * dashes with daylight between them, and the heavy characters – {@code ━}, {@code ┏} – are not
 * heavy at all, because the font has no heavy forms and falls back to the thin ones. What the font
 * does draw as a solid line of any width you like is an underline, and a run of underlined blanks
 * is exactly that: unbroken, and as long as it was paid for. So the sides are characters and the
 * top and bottom are underlines, which is the pair that actually looks like a frame.
 *
 * <h2>Why the right-hand bar is set so far in</h2>
 *
 * <p>Because it has to be in the same place on every line, and a line is walked out to it with
 * spaces. Spaces are four pixels, five in bold, so gaps of one, two, three, six, seven and eleven
 * pixels cannot be written at all – but every gap of twelve or more can. So twelve pixels are kept
 * clear on the right whatever the line does, and the bar never moves.
 */
public enum FrameStyle {
	NONE(' ', false, "", ""),
	/** A single line all the way round. */
	LIGHT('│', false, "", ""),
	/** Two lines top and bottom, and a double bar down each side. */
	DOUBLE('║', true, "", ""),
	/** A single line with a flourish in each corner. */
	ORNATE('│', false, "❦", "❧");

	/** How much room is always left between the text and the right-hand bar. */
	public static final float RIGHT_MARGIN = 12.0f;
	/** And between the left-hand bar and the text, where a fixed gap is always expressible. */
	public static final float LEFT_MARGIN = 4.0f;

	public final char bar;
	/** Whether the top and bottom are two lines rather than one. */
	public final boolean twoLines;
	private final String leftCap;
	private final String rightCap;

	FrameStyle(char bar, boolean twoLines, String leftCap, String rightCap) {
		this.bar = bar;
		this.twoLines = twoLines;
		this.leftCap = leftCap;
		this.rightCap = rightCap;
	}

	public boolean present() {
		return this != NONE;
	}

	public Text label() {
		return Text.translatable("roleplayersquill.frame." + name().toLowerCase(Locale.ROOT));
	}

	public float barWidth() {
		return present() ? Widths.advance(bar, false) : 0.0f;
	}

	/** Where the text starts, measured from the left edge of the page. */
	public float textLeft() {
		return present() ? barWidth() + LEFT_MARGIN : 0.0f;
	}

	/** Where the right-hand bar stands. */
	public float barRight() {
		return Layout.PAGE_WIDTH - barWidth();
	}

	/** How wide the text inside may be. */
	public float inner() {
		return present() ? barRight() - RIGHT_MARGIN - textLeft() : Layout.PAGE_WIDTH;
	}

	/**
	 * The top or bottom of the frame as a line of its own: a solid rule, with a flourish at each end
	 * where the style calls for one.
	 */
	public Paragraph edge(boolean top) {
		Paragraph out = new Paragraph();
		if (!present()) {
			return out;
		}
		QuillStyle ruled = QuillStyle.PLAIN.withUnderlined(true).withStrikethrough(twoLines);
		String left = top ? leftCap : rightCap;
		String right = top ? rightCap : leftCap;
		float caps = Widths.widthOf(left, false) + Widths.widthOf(right, false);

		if (!left.isEmpty()) {
			out.insert(out.length(), left, QuillStyle.PLAIN);
		}
		Widths.Padding rule = Widths.pad(Layout.PAGE_WIDTH - caps);
		int plain = rule.count() - rule.bold();
		if (plain > 0) {
			out.insert(out.length(), " ".repeat(plain), ruled);
		}
		if (rule.bold() > 0) {
			out.insert(out.length(), " ".repeat(rule.bold()), ruled.withBold(true));
		}
		if (!right.isEmpty()) {
			out.insert(out.length(), right, QuillStyle.PLAIN);
		}
		return out;
	}

	/** Whether a paragraph is one of those edges, for taking a frame off again. */
	public static boolean isEdge(Paragraph paragraph) {
		if (paragraph.isEmpty() || paragraph.frame().present()) {
			return false;
		}
		boolean ruled = false;
		for (int i = 0; i < paragraph.length(); i++) {
			char c = paragraph.charAt(i);
			QuillStyle style = paragraph.styleAt(i);
			if (c == ' ') {
				if (!style.underlined()) {
					return false;
				}
				ruled = true;
				continue;
			}
			// Anything else has to be one of the flourishes a frame puts in its corners.
			boolean cap = false;
			for (FrameStyle frame : values()) {
				cap |= frame.leftCap.indexOf(c) >= 0 || frame.rightCap.indexOf(c) >= 0;
			}
			if (!cap) {
				return false;
			}
		}
		return ruled;
	}
}
