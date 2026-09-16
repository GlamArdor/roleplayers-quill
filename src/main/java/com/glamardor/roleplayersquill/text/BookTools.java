package com.glamardor.roleplayersquill.text;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The things that are built out of a whole book rather than out of a line: a contents page, a
 * footnote, the ready-made books.
 */
public final class BookTools {
	/** The superscript figures, which is as far as Minecraft's font goes. */
	private static final String[] SUPERSCRIPT = {"⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"};

	private BookTools() {
	}

	/** A number in the small raised figures a footnote is marked with. */
	public static String superscript(int number) {
		StringBuilder out = new StringBuilder();
		for (char digit : Integer.toString(Math.max(0, number)).toCharArray()) {
			out.append(SUPERSCRIPT[digit - '0']);
		}
		return out.toString();
	}

	/** How many footnotes are already marked on a page. */
	public static int footnotesOn(List<Paragraph> page) {
		int found = 0;
		for (Paragraph paragraph : page) {
			String text = paragraph.text();
			for (int i = 0; i < text.length(); i++) {
				if (isSuperscript(text.charAt(i)) && (i == 0 || !isSuperscript(text.charAt(i - 1)))) {
					found++;
				}
			}
		}
		// Every note is marked twice: once in the text and once against the note itself.
		return found / 2;
	}

	private static boolean isSuperscript(char c) {
		for (String digit : SUPERSCRIPT) {
			if (digit.charAt(0) == c) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A contents page for the book, from the headings in it.
	 *
	 * <p>A book remembers nothing about headings – a written page is a flat string – so what counts
	 * as one is what a heading looks like: bold and centred, which is exactly what the paragraph
	 * style puts there. Anything set that way is listed.
	 *
	 * @param pages the book as it stands, before the contents are put in front of it
	 * @return the pages of the contents, ready to be inserted at the front, or empty if there are
	 *         no headings to list
	 */
	public static List<List<Paragraph>> contentsFor(List<List<Paragraph>> pages, Text title) {
		record Entry(String text, int page, boolean under) {
		}
		List<Entry> entries = new ArrayList<>();
		String titleText = title.getString();
		for (int p = 0; p < pages.size(); p++) {
			for (Paragraph paragraph : pages.get(p)) {
				if (paragraph.isEmpty()) {
					continue;
				}
				// Subheadings are listed too, a step in from the headings they sit under. A book with
				// chapters and sections in it is exactly the book that wants contents at all.
				ParagraphStyle style = ParagraphStyle.of(paragraph);
				if (style != ParagraphStyle.HEADING && style != ParagraphStyle.SUBHEADING) {
					continue;
				}
				String text = paragraph.text().trim();
				// Not the contents' own heading, so that building them twice does not list them.
				if (!text.isEmpty() && !text.equals(titleText)) {
					entries.add(new Entry(text, p, style == ParagraphStyle.SUBHEADING));
				}
			}
		}
		if (entries.isEmpty()) {
			return List.of();
		}

		// The contents push everything along, so how many pages they take has to be known before the
		// numbers in them can be written. The title costs a line on the first page.
		int perPage = Layout.PAGE_LINES;
		int taken = Math.max(1, (entries.size() + 1 + perPage - 1) / perPage);

		List<List<Paragraph>> out = new ArrayList<>();
		List<Paragraph> page = new ArrayList<>();
		Paragraph heading = new Paragraph(titleText, QuillStyle.PLAIN);
		ParagraphStyle.HEADING.applyTo(heading);
		page.add(heading);

		for (Entry entry : entries) {
			if (page.size() >= perPage) {
				out.add(page);
				page = new ArrayList<>();
			}
			Paragraph line = new Paragraph();
			if (entry.under()) {
				line.setIndent(1);
			}
			line.insert(0, entry.text(), QuillStyle.PLAIN);
			line.insert(line.length(), String.valueOf(Widths.LEADER), QuillStyle.PLAIN);
			line.insert(line.length(), Integer.toString(entry.page() + taken + 1), QuillStyle.PLAIN);
			page.add(line);
		}
		out.add(page);
		return out;
	}
}
