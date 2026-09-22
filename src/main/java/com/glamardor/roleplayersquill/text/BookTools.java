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

	/**
	 * How many words are written on a page.
	 *
	 * <p>Counted the way a person counts them: a run of letters or figures is a word, and the
	 * punctuation, the blanks and the list markers around it are not. A number is a word, because in
	 * "тридцать монет" and "30 монет" nobody would say the second is shorter by one.
	 */
	public static int wordsOn(List<Paragraph> page) {
		int words = 0;
		for (Paragraph paragraph : page) {
			String text = paragraph.text();
			boolean inside = false;
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				boolean letter = Character.isLetterOrDigit(c)
						|| (c == '-' || c == '\'' || c == '’') && inside;
				if (letter && !inside) {
					words++;
				}
				inside = letter;
			}
		}
		return words;
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

	/** One heading, wherever it was found. */
	public record Heading(String text, int page, boolean under) {
	}

	/**
	 * Every heading and subheading in the book, in reading order.
	 *
	 * <p>A book remembers nothing about headings – a written page is a flat string – so what counts
	 * as one is what a heading looks like: bold and centred, which is exactly what the paragraph
	 * style puts there. Anything set that way is a heading, whether this mod wrote it, an older
	 * version of it did, or a page was typed by hand to look the same way.
	 *
	 * @param skip a heading to leave out even so – the contents' own title, when contents are being
	 *             built for a book that may already have a copy of them in it
	 */
	public static List<Heading> headings(List<List<Paragraph>> pages, String skip) {
		List<Heading> entries = new ArrayList<>();
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
				if (!text.isEmpty() && !text.equals(skip)) {
					entries.add(new Heading(text, p, style == ParagraphStyle.SUBHEADING));
				}
			}
		}
		return entries;
	}

	/**
	 * A contents page for the book, from the headings in it.
	 *
	 * @param pages the book as it stands, before the contents are put in front of it
	 * @return the pages of the contents, ready to be inserted at the front, or empty if there are
	 *         no headings to list
	 */
	public static List<List<Paragraph>> contentsFor(List<List<Paragraph>> pages, Text title) {
		String titleText = title.getString();
		List<Heading> entries = headings(pages, titleText);
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

		for (Heading entry : entries) {
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
