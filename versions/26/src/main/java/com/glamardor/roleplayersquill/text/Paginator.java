package com.glamardor.roleplayersquill.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Pours a stream of text into pages.
 *
 * <p>Pasting a chapter into a book should produce a chapter, not the first fourteen lines of one
 * and a lost remainder. So text longer than a page is laid out as one continuous flow and then cut
 * where the page ends – at a line break, never in the middle of one – and the cut is checked
 * against both limits the game keeps: fourteen lines of height and a thousand-odd characters of
 * length, the second of which is the one that bites, because every {@code §} code and every pad
 * space is spending it.
 *
 * <p>A word that the cut lands inside is either broken with a hyphen or moved down whole. Which of
 * the two is the player's to choose: a hyphen across a page turn reads badly to some people and is
 * simply how books are set to others.
 */
public final class Paginator {
	private Paginator() {
	}

	/**
	 * @param hyphenateAtPageBreak break the word the page ends inside, instead of moving it down whole
	 * @param maxChars             the page limit to respect, normally {@link QuillDocument#MAX_PAGE_CHARS}
	 */
	public record Options(Layout.Options layout, boolean hyphenateAtPageBreak, int maxChars, int maxLines) {
		public static Options of(Layout.Options layout, boolean hyphenateAtPageBreak) {
			return new Options(layout, hyphenateAtPageBreak, QuillDocument.MAX_PAGE_CHARS, Layout.PAGE_LINES);
		}
	}

	/** One page's worth taken off the front, and whatever is left. */
	private record Split(List<Paragraph> head, List<Paragraph> tail) {
	}

	public static List<List<Paragraph>> paginate(List<Paragraph> flow, Options options) {
		List<List<Paragraph>> pages = new ArrayList<>();
		List<Paragraph> rest = QuillDocument.copyPage(flow);
		while (true) {
			Split split = takePage(rest, options);
			pages.add(split.head());
			if (split.tail().isEmpty() || pages.size() >= QuillDocument.MAX_PAGES) {
				break;
			}
			rest = split.tail();
		}
		return pages;
	}

	/** How many pages a body of text would need, without building them. */
	public static int countPages(List<Paragraph> flow, Options options) {
		return paginate(flow, options).size();
	}

	private static Split takePage(List<Paragraph> rest, Options options) {
		List<Layout.LaidLine> lines = Layout.lay(rest, options.layout());
		int keep = Math.min(options.maxLines(), lines.size());

		while (true) {
			if (keep >= lines.size()) {
				List<Paragraph> whole = QuillDocument.copyPage(rest);
				if (fits(whole, options) || keep <= 1) {
					return new Split(whole, List.of());
				}
				keep = lines.size() - 1;
				continue;
			}

			Boundary boundary = boundaryAfter(lines, rest, keep, options.hyphenateAtPageBreak());
			if (boundary == null) {
				// Every candidate break left nothing on the page; take the line as it falls.
				boundary = new Boundary(lines.get(keep).paragraph, lines.get(keep).start);
			}
			Split split = sliceAt(rest, boundary);
			if (fits(split.head(), options) || keep <= 1) {
				return split;
			}
			keep--;
		}
	}

	private static boolean fits(List<Paragraph> page, Options options) {
		List<Layout.LaidLine> lines = Layout.lay(page, options.layout());
		if (lines.size() > options.maxLines()) {
			return false;
		}
		return LegacyCodec.encode(page, lines).length() <= options.maxChars();
	}

	/** Where the next page starts, once the word policy has had its say. */
	private static Boundary boundaryAfter(List<Layout.LaidLine> lines, List<Paragraph> flow, int keep,
			boolean hyphenate) {
		Layout.LaidLine last = lines.get(keep - 1);
		Layout.LaidLine next = lines.get(keep);
		if (!last.hyphen || hyphenate) {
			return new Boundary(next.paragraph, next.start);
		}
		// The page ends inside a word and the player asked for the word to travel whole.
		Paragraph paragraph = flow.get(last.paragraph);
		int wordStart = last.contentEnd;
		while (wordStart > last.start && paragraph.charAt(wordStart - 1) != ' ') {
			wordStart--;
		}
		if (wordStart > last.start) {
			return new Boundary(last.paragraph, wordStart);
		}
		if (keep <= 1) {
			return null;
		}
		return new Boundary(last.paragraph, last.start);
	}

	private record Boundary(int paragraph, int index) {
	}

	private static Split sliceAt(List<Paragraph> flow, Boundary boundary) {
		List<Paragraph> head = new ArrayList<>();
		List<Paragraph> tail = new ArrayList<>();

		for (int i = 0; i < boundary.paragraph(); i++) {
			head.add(flow.get(i).copy());
		}
		Paragraph cut = flow.get(boundary.paragraph());
		int index = Math.max(0, Math.min(boundary.index(), cut.length()));

		if (index > 0) {
			Paragraph near = cut.slice(0, index);
			if (index < cut.length()) {
				near.setContinues(true);
			}
			head.add(near);
		}
		if (index < cut.length()) {
			Paragraph far = cut.slice(index, cut.length());
			if (index > 0) {
				far.setContinuation(true);
			}
			tail.add(far);
		}
		for (int i = boundary.paragraph() + 1; i < flow.size(); i++) {
			tail.add(flow.get(i).copy());
		}

		if (head.isEmpty()) {
			head.add(new Paragraph());
		}
		if (tail.size() == 1 && tail.get(0).isEmpty() && !tail.get(0).isContinuation()) {
			tail.clear();
		}
		return new Split(head, tail);
	}

	// ---- turning a file or a clipboard into paragraphs ------------------------------------------

	/**
	 * Reads plain text into paragraphs.
	 *
	 * @param blankLineParagraphs treat a single line break as a soft one and only a blank line as the
	 *                            end of a paragraph, which is what a hard-wrapped text file wants;
	 *                            otherwise every line break starts a paragraph
	 */
	public static List<Paragraph> parse(String text, boolean blankLineParagraphs, QuillStyle style,
			Alignment alignment) {
		List<Paragraph> paragraphs = new ArrayList<>();
		String normalised = text.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');

		if (blankLineParagraphs) {
			StringBuilder current = new StringBuilder();
			for (String line : normalised.split("\n", -1)) {
				if (line.isBlank()) {
					paragraphs.add(make(current.toString(), style, alignment));
					current.setLength(0);
				} else {
					if (!current.isEmpty()) {
						current.append(' ');
					}
					current.append(line.strip());
				}
			}
			paragraphs.add(make(current.toString(), style, alignment));
			// A file that ends with a blank line should not add an empty paragraph to the book.
			while (paragraphs.size() > 1 && paragraphs.get(paragraphs.size() - 1).isEmpty()) {
				paragraphs.remove(paragraphs.size() - 1);
			}
		} else {
			for (String line : normalised.split("\n", -1)) {
				paragraphs.add(make(line, style, alignment));
			}
		}
		if (paragraphs.isEmpty()) {
			paragraphs.add(new Paragraph());
		}
		return paragraphs;
	}

	private static Paragraph make(String line, QuillStyle style, Alignment alignment) {
		Paragraph paragraph = new Paragraph();
		// Codes already in the file are honoured: a text file exported from this very editor has them.
		if (line.indexOf(LegacyCodec.SECTION) >= 0) {
			List<Paragraph> decoded = LegacyCodec.decode(line);
			paragraph = decoded.get(0);
		} else if (!line.isEmpty()) {
			paragraph.insert(0, line, style);
		}
		paragraph.setAlignment(alignment);
		return paragraph;
	}
}
