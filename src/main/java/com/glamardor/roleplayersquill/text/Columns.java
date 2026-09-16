package com.glamardor.roleplayersquill.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Setting a page in two columns.
 *
 * <p>A book page has no notion of a column, so this is a change made to the text rather than a way
 * of showing it: the page is laid out narrow, the lines are dealt into two heaps, and each row of
 * the finished page is a left line, a run of spaces and a right line. What comes out is ordinary
 * paragraphs, which is why a reader without the mod sees the same two columns.
 *
 * <p>Which also means it cannot be undone by pressing the button again – the paragraphs are gone,
 * the same way they are gone after the text is reflowed. Undo is the way back.
 */
public final class Columns {
	/**
	 * How wide each column is, and where the second one starts.
	 *
	 * <p>Forty-eight and sixty, not fifty-four and fifty-seven, and for a reason worth writing down:
	 * the right column is reached by writing spaces, and spaces come in fours and fives, so the gap
	 * between the end of a left line and the start of the right column has to be a width they can
	 * make. Widths of one, two, three, six, seven and eleven pixels cannot be made at all. Every gap
	 * of twelve or more can, so the columns are set far enough apart that no gap is ever smaller –
	 * and then no line of the right column is ever a pixel out of place.
	 */
	public static final float COLUMN_WIDTH = 48.0f;
	public static final float SECOND_COLUMN = 60.0f;

	private Columns() {
	}

	/**
	 * Sets a page in two columns, overflowing into as many pages as it takes.
	 *
	 * @return the pages to put in place of the one given
	 */
	public static List<List<Paragraph>> split(List<Paragraph> page, Layout.Options options) {
		List<Layout.LaidLine> lines = Layout.lay(page, options, COLUMN_WIDTH);
		if (lines.isEmpty()) {
			return List.of(new ArrayList<>(page));
		}

		int perColumn = Layout.PAGE_LINES;
		int perPage = perColumn * 2;
		List<List<Paragraph>> out = new ArrayList<>();

		for (int start = 0; start < lines.size(); start += perPage) {
			List<Paragraph> built = new ArrayList<>();
			for (int row = 0; row < perColumn; row++) {
				int left = start + row;
				int right = start + perColumn + row;
				if (left >= lines.size()) {
					break;
				}
				built.add(row(page, lines, left, right < lines.size() ? right : -1));
			}
			out.add(built);
		}
		return out;
	}

	/** One row of the finished page: the left line, the gap, and the right one. */
	private static Paragraph row(List<Paragraph> page, List<Layout.LaidLine> lines, int left, int right) {
		Paragraph out = sliceOf(page, lines.get(left));
		if (right < 0) {
			return out;
		}
		Paragraph tail = sliceOf(page, lines.get(right));
		if (tail.isEmpty()) {
			return out;
		}
		float used = width(out);
		Widths.Padding pad = Widths.pad(SECOND_COLUMN - used);
		int plain = pad.count() - pad.bold();
		if (plain > 0) {
			out.insert(out.length(), " ".repeat(plain), QuillStyle.PLAIN);
		}
		if (pad.bold() > 0) {
			out.insert(out.length(), " ".repeat(pad.bold()), QuillStyle.PLAIN.withBold(true));
		}
		out.append(tail);
		return out;
	}

	/** A line's text, with its formatting, as a paragraph of its own. */
	private static Paragraph sliceOf(List<Paragraph> page, Layout.LaidLine line) {
		Paragraph source = page.get(line.paragraph);
		Paragraph out = line.contentEnd > line.start
				? source.slice(line.start, line.contentEnd)
				: new Paragraph();
		// Set flush left whatever the paragraph it came from was: a column is its own margin, and a
		// line centred inside forty-eight pixels in the middle of a page reads as a mistake.
		out.setAlignment(Alignment.LEFT);
		out.setList(ListStyle.NONE);
		out.setIndent(0);
		return out;
	}

	private static float width(Paragraph paragraph) {
		float total = 0.0f;
		for (int i = 0; i < paragraph.length(); i++) {
			total += Widths.advance(paragraph.charAt(i), paragraph.styleAt(i).bold());
		}
		return total;
	}
}
