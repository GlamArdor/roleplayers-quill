package com.glamardor.roleplayersquill.text;

import net.minecraft.text.Text;

/**
 * A whole paragraph's look in one word: heading, quotation, caption.
 *
 * <p>Nothing here cannot be done by hand with the buttons already on the toolbar – bold, centred,
 * indented. The point is that it takes four presses to do by hand and one to do here, and that a
 * book written this way is consistent: every heading in it is the same kind of heading, rather than
 * whatever the writer felt like on that page.
 *
 * <p>It is also what makes a table of contents possible at all. A page is a flat string by the time
 * it is written, so nothing in a book says "this line is a heading" – but a line that is bold and
 * centred is one, near enough, and that is exactly what this puts there.
 */
public enum ParagraphStyle {
	/** Plain text: no weight of its own, along the left margin. */
	NORMAL,
	/** Bold and centred. What the contents page looks for. */
	HEADING,
	/** Bold, along the margin: the next step down. */
	SUBHEADING,
	/** Italic and indented, the way a quotation is set. */
	QUOTE,
	/** Italic and to the right: a signature, a date, an attribution. */
	CAPTION;

	public Text label() {
		return Text.translatable("roleplayersquill.paragraph." + name().toLowerCase(java.util.Locale.ROOT));
	}

	/**
	 * Sets the paragraph to this style.
	 *
	 * <p>Weight and slant only. Colour, links and everything else stay as they were: making a line
	 * a heading should not throw away that it was written in red, and a writer who wanted it black
	 * has a colour button for that.
	 */
	/** The weight and slant this style wants, kept on top of whatever else a style carries. */
	public QuillStyle runStyle(QuillStyle base) {
		return base.withBold(this == HEADING || this == SUBHEADING)
				.withItalic(this == QUOTE || this == CAPTION);
	}

	public void applyTo(Paragraph paragraph) {
		boolean bold = this == HEADING || this == SUBHEADING;
		boolean italic = this == QUOTE || this == CAPTION;
		if (!paragraph.isEmpty()) {
			paragraph.restyle(0, paragraph.length(), style -> style.withBold(bold).withItalic(italic));
		}
		paragraph.setAlignment(switch (this) {
			case HEADING -> Alignment.CENTER;
			case CAPTION -> Alignment.RIGHT;
			default -> Alignment.LEFT;
		});
		paragraph.setIndent(this == QUOTE ? 1 : 0);
		if (this != NORMAL) {
			paragraph.setList(ListStyle.NONE);
		}
	}

	/**
	 * What a paragraph already looks like, as far as it can be told.
	 *
	 * <p>Read off the same marks the style puts there, so that reopening a book shows the right
	 * entry ticked even though the book itself remembers none of this.
	 */
	public static ParagraphStyle of(Paragraph paragraph) {
		if (paragraph.isEmpty()) {
			return NORMAL;
		}
		QuillStyle first = paragraph.styleAt(0);
		if (first.bold()) {
			return paragraph.alignment() == Alignment.CENTER ? HEADING : SUBHEADING;
		}
		if (first.italic()) {
			return paragraph.alignment() == Alignment.RIGHT ? CAPTION : QUOTE;
		}
		return NORMAL;
	}
}
