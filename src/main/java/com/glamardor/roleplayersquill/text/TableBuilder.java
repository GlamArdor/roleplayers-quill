package com.glamardor.roleplayersquill.text;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tables, out of nothing but spaces.
 *
 * <p>A book page has no table anything: no cells, no tab stops, no way to say "this column starts
 * here". What it does have is a font whose every glyph is a known number of pixels wide, and a
 * space that is four of them – five in bold. So a column that starts at pixel forty starts at pixel
 * forty for every reader, mod or no mod, because the spaces in front of it add up to forty.
 *
 * <p>The rules between the rows are spaces too. A run of blanks with {@code §n} on it is an
 * underline, and an underline is a horizontal line of exactly the width you paid for – which is a
 * good deal cleaner than a row of dashes, and does not depend on the font having box drawing.
 */
public final class TableBuilder {
	/** How the columns are told apart. */
	public enum Style {
		/** Two spaces and nothing else. Quiet, and the most room for the text. */
		PLAIN("plain"),
		/** A rule under the heading row. */
		RULES("rules"),
		/** A rule under the heading and a bar between the columns. */
		GRID("grid");

		private final String id;

		Style(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public Style next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public net.minecraft.text.Text label() {
			return net.minecraft.text.Text.translatable("roleplayersquill.table.style." + id);
		}
	}

	/**
	 * What the first row is, and what it looks like.
	 *
	 * <p>Bold is the obvious answer and not always the right one: a heading in a small book can read
	 * better underlined, and a table inside a bold paragraph wants its heading to stand out some
	 * other way entirely.
	 */
	public enum Heading {
		NONE("none", QuillStyle.PLAIN),
		BOLD("bold", QuillStyle.PLAIN.withBold(true)),
		ITALIC("italic", QuillStyle.PLAIN.withItalic(true)),
		UNDERLINE("underline", QuillStyle.PLAIN.withUnderlined(true)),
		BOLD_UNDERLINE("bold_underline", QuillStyle.PLAIN.withBold(true).withUnderlined(true));

		private final String id;
		private final QuillStyle style;

		Heading(String id, QuillStyle style) {
			this.id = id;
			this.style = style;
		}

		public boolean present() {
			return this != NONE;
		}

		public QuillStyle style() {
			return style;
		}

		public Heading next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public net.minecraft.text.Text label() {
			return net.minecraft.text.Text.translatable("roleplayersquill.table.heading." + id);
		}
	}

	/** How the text sits in its column. */
	public enum Columns {
		/** Text to the left, numbers to the right, which is what a reader expects of both. */
		AUTO("auto"),
		LEFT("left"),
		CENTER("center"),
		RIGHT("right");

		private final String id;

		Columns(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public Columns next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public net.minecraft.text.Text label() {
			return net.minecraft.text.Text.translatable("roleplayersquill.table.columns." + id);
		}
	}

	private static final char BAR = '│';
	private static final String ELLIPSIS = "…";

	private TableBuilder() {
	}

	/** Splits what the player typed into cells: a line is a row, a bar or a tab ends a cell. */
	public static List<List<String>> parse(String source) {
		List<List<String>> rows = new ArrayList<>();
		for (String line : source.replace("\r\n", "\n").split("\n")) {
			if (line.isBlank()) {
				continue;
			}
			List<String> cells = new ArrayList<>();
			for (String cell : line.split("\\||\t", -1)) {
				cells.add(cell.strip());
			}
			// A leading or trailing bar is how tables are written in a lot of places; it should not
			// produce an empty column at either end.
			if (cells.size() > 1 && cells.get(0).isEmpty()) {
				cells.remove(0);
			}
			if (cells.size() > 1 && cells.get(cells.size() - 1).isEmpty()) {
				cells.remove(cells.size() - 1);
			}
			rows.add(cells);
		}
		return rows;
	}

	/**
	 * Lays a table out into paragraphs, one per row.
	 *
	 * @param heading what the first row is, and how it is set apart from the rest
	 * @return the rows, or an empty list when there is nothing to lay out
	 */
	public static List<Paragraph> build(List<List<String>> rows, Style style, Columns columns, Heading heading) {
		if (rows.isEmpty()) {
			return List.of();
		}
		int count = 0;
		for (List<String> row : rows) {
			count = Math.max(count, row.size());
		}
		if (count == 0) {
			return List.of();
		}

		float space = Widths.space();
		float separator = style == Style.GRID
				? Widths.advance(BAR, false) + 2 * space
				: 2 * space;
		float available = Layout.PAGE_WIDTH - separator * (count - 1);

		float[] widths = measure(rows, count, available, heading);
		boolean[] rightAligned = alignments(rows, count, columns, heading);
		widen(rows, widths, count, available, style, columns, rightAligned, heading);

		float[][] at = positions(widths, count, style);
		float[] columnStart = at[0];
		float[] barAt = at[1];

		List<Paragraph> out = new ArrayList<>();
		// A rule belongs under the heading. With no heading it goes on top instead, so that asking
		// for a ruled table always gets a line rather than silently getting the plain one.
		if (!heading.present() && style != Style.PLAIN) {
			out.add(rule());
		}

		for (int r = 0; r < rows.size(); r++) {
			boolean isHeading = heading.present() && r == 0;
			Paragraph paragraph = new Paragraph();
			writeRow(paragraph, rows.get(r), r, widths, columnStart, barAt, count, style, columns,
					rightAligned, heading);
			out.add(paragraph);

			if (isHeading && style != Style.PLAIN) {
				out.add(rule());
			}
		}
		return out;
	}

	/**
	 * Whether the table can be printed as written, or whether something will have to be cut.
	 *
	 * <p>114 pixels is about nineteen Cyrillic letters. Three columns of real words do not fit, and
	 * a builder that silently trims them to fit is a builder that quietly loses half a word.
	 */
	public static boolean fits(List<List<String>> rows, Style style, Heading heading) {
		if (rows.isEmpty()) {
			return true;
		}
		int count = 0;
		for (List<String> row : rows) {
			count = Math.max(count, row.size());
		}
		float separator = style == Style.GRID
				? Widths.advance(BAR, false) + 2 * Widths.space()
				: 2 * Widths.space();
		float total = separator * (count - 1);
		float[] widths = new float[count];
		for (int r = 0; r < rows.size(); r++) {
			boolean bold = heading.present() && r == 0 && heading.style().bold();
			List<String> row = rows.get(r);
			for (int c = 0; c < row.size() && c < count; c++) {
				widths[c] = Math.max(widths[c], Widths.widthOf(row.get(c), bold));
			}
		}
		for (float width : widths) {
			total += width;
		}
		return total <= Layout.PAGE_WIDTH;
	}

	/** A full-width line, drawn as underlined blanks. */
	public static Paragraph rule() {
		Paragraph paragraph = new Paragraph();
		Widths.Padding padding = Widths.pad(Layout.PAGE_WIDTH);
		int plain = padding.count() - padding.bold();
		if (plain > 0) {
			paragraph.insert(paragraph.length(), " ".repeat(plain), QuillStyle.PLAIN.withUnderlined(true));
		}
		if (padding.bold() > 0) {
			paragraph.insert(paragraph.length(), " ".repeat(padding.bold()),
					QuillStyle.PLAIN.withUnderlined(true).withBold(true));
		}
		return paragraph;
	}

	/**
	 * Column widths: what each column wants, shrunk to what the page has.
	 *
	 * <p>When it does not fit, the widest column gives way first and keeps giving way until it does.
	 * Taking the same slice off every column instead would cut the short ones to nothing.
	 */
	private static float[] measure(List<List<String>> rows, int count, float available, Heading heading) {
		float[] widths = new float[count];
		for (int r = 0; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			// The heading is printed bold, and bold is a pixel wider per letter. Measuring it plain
			// is how a heading ends up truncated in a column that was sized to hold it.
			boolean bold = heading.present() && r == 0 && heading.style().bold();
			for (int c = 0; c < row.size() && c < count; c++) {
				widths[c] = Math.max(widths[c], Widths.widthOf(row.get(c), bold));
			}
		}
		float total = 0.0f;
		for (float width : widths) {
			total += width;
		}
		int guard = 0;
		while (total > available && guard++ < 400) {
			int widest = 0;
			for (int c = 1; c < count; c++) {
				if (widths[c] > widths[widest]) {
					widest = c;
				}
			}
			float take = Math.min(4.0f, widths[widest]);
			widths[widest] -= take;
			total -= take;
		}
		for (int c = 0; c < count; c++) {
			widths[c] = Math.max(widths[c], Widths.advance('m', false));
		}
		return widths;
	}

	/**
	 * Where the text sits inside its column, as a whole number of pixels.
	 *
	 * <p>Whole numbers because a run of spaces cannot be half a pixel wide. Centred text used to ask
	 * for half the slack, which is a fraction as often as not, and from there every position in the
	 * row was a fraction – so the bar after it landed a pixel out on some rows and not on others.
	 */
	private static float offsetWithin(float slack, Columns columns, boolean rightAligned) {
		float ideal = switch (columns) {
			case CENTER -> slack / 2.0f;
			case RIGHT -> slack;
			case LEFT -> 0.0f;
			case AUTO -> rightAligned ? slack : 0.0f;
		};
		return (float) Math.floor(ideal);
	}

	/**
	 * Where every column and every bar stands, measured from the left edge of the page.
	 *
	 * <p>Rows are written by padding to these numbers rather than by padding out each cell on its
	 * own: two paddings in a row each lose up to three pixels to what a run of spaces can express,
	 * and the losses differ from row to row, which is exactly how a column of bars comes out looking
	 * like a staircase.
	 *
	 * @return the starts of the columns, and the places the bars go, in that order
	 */
	private static float[][] positions(float[] widths, int count, Style style) {
		float space = Widths.space();
		float barWidth = Widths.advance(BAR, false);
		float separator = style == Style.GRID ? barWidth + 2 * space : 2 * space;
		float[] columnStart = new float[count];
		float[] barAt = new float[count];
		for (int c = 1; c < count; c++) {
			barAt[c] = columnStart[c - 1] + widths[c - 1] + space;
			columnStart[c] = style == Style.GRID
					? barAt[c] + barWidth + space
					: columnStart[c - 1] + widths[c - 1] + separator;
		}
		return new float[][] {columnStart, barAt};
	}

	/**
	 * Writes one row, and reports how many times it could not reach where it was aiming.
	 *
	 * <p>Called twice over: once with a paragraph to write into, and once with none, while the
	 * column widths are still being chosen. Having one method do both is the point – a separate
	 * model of what the writing would do is a model that drifts, and the staircase this is here to
	 * prevent is exactly what drift looks like on the page.
	 *
	 * @param paragraph where to write, or null to work out the misses without writing anything
	 * @return how many targets in this row a run of spaces could not reach exactly
	 */
	private static int writeRow(@Nullable Paragraph paragraph, List<String> row, int index,
			float[] widths, float[] columnStart, float[] barAt, int count, Style style,
			Columns columns, boolean[] rightAligned, Heading heading) {
		QuillStyle cellStyle = heading.present() && index == 0 ? heading.style() : QuillStyle.PLAIN;
		float barWidth = Widths.advance(BAR, false);
		float x = 0.0f;
		int missed = 0;

		for (int c = 0; c < count; c++) {
			if (c > 0 && style == Style.GRID) {
				x = padTo(paragraph, x, barAt[c]);
				missed += x < barAt[c] - 0.001f ? 1 : 0;
				if (paragraph != null) {
					paragraph.insert(paragraph.length(), String.valueOf(BAR), QuillStyle.PLAIN);
				}
				x += barWidth;
			}

			String cell = c < row.size() ? row.get(c) : "";
			cell = trimTo(cell, widths[c], cellStyle.bold());
			float used = Widths.widthOf(cell, cellStyle.bold());
			float slack = Math.max(0.0f, widths[c] - used);
			float before = offsetWithin(slack, columns, rightAligned[c]);
			float target = columnStart[c] + before;

			x = padTo(paragraph, x, target);
			missed += x < target - 0.001f ? 1 : 0;
			if (paragraph != null) {
				paragraph.insert(paragraph.length(), cell, cellStyle);
			}
			x += used;
		}
		return missed;
	}

	/** How many places in the whole table a row would stop short of, at these widths. */
	private static int misses(List<List<String>> rows, float[] widths, int count, Style style,
			Columns columns, boolean[] rightAligned, Heading heading) {
		float[][] at = positions(widths, count, style);
		int total = 0;
		for (int r = 0; r < rows.size(); r++) {
			total += writeRow(null, rows.get(r), r, widths, at[0], at[1], count, style, columns,
					rightAligned, heading);
		}
		return total;
	}

	/**
	 * Widens columns by a few pixels where that lets every row reach its mark exactly.
	 *
	 * <p>A row walks to the next bar by writing spaces, and spaces come in fours and fives – so a
	 * gap of one, two, three, six, seven or eleven pixels cannot be written at all, and the row
	 * stops short. Which rows that happens to depends on the words in them, so the bars come out in
	 * a staircase.
	 *
	 * <p>A column a pixel or two wider moves every gap in it along by the same amount, and there is
	 * almost always an amount that clears all the holes at once. The search goes as wide as the page
	 * allows rather than stopping at four, because with several rows the first few widths can all be
	 * blocked by different rows; and when nothing is perfect, the width with the fewest bad rows is
	 * kept rather than the one we started with.
	 */
	private static void widen(List<List<String>> rows, float[] widths, int count, float available,
			Style style, Columns columns, boolean[] rightAligned, Heading heading) {
		float total = 0.0f;
		for (float width : widths) {
			total += width;
		}

		for (int c = 0; c < count; c++) {
			int best = 0;
			int fewest = Integer.MAX_VALUE;
			float was = widths[c];
			for (int delta = 0; delta <= WIDEN_LIMIT; delta++) {
				if (total + delta > available) {
					break;
				}
				widths[c] = was + delta;
				int bad = misses(rows, widths, count, style, columns, rightAligned, heading);
				if (bad < fewest) {
					fewest = bad;
					best = delta;
					if (bad == 0) {
						break;
					}
				}
			}
			widths[c] = was + best;
			total += best;
		}
	}

	/** How far a column may be widened in search of a width every row can reach. */
	private static final int WIDEN_LIMIT = 16;

	/** A column of numbers reads better flush right; a column of words does not. */
	private static boolean[] alignments(List<List<String>> rows, int count, Columns columns, Heading heading) {
		boolean[] right = new boolean[count];
		if (columns != Columns.AUTO) {
			return right;
		}
		for (int c = 0; c < count; c++) {
			boolean allNumbers = true;
			boolean any = false;
			for (int r = heading.present() ? 1 : 0; r < rows.size(); r++) {
				List<String> row = rows.get(r);
				if (c >= row.size() || row.get(c).isEmpty()) {
					continue;
				}
				any = true;
				if (!isNumber(row.get(c))) {
					allNumbers = false;
					break;
				}
			}
			right[c] = any && allNumbers;
		}
		return right;
	}

	private static boolean isNumber(String cell) {
		for (int i = 0; i < cell.length(); i++) {
			char c = cell.charAt(i);
			if (!Character.isDigit(c) && c != '.' && c != ',' && c != '-' && c != '+' && c != '%'
					&& c != ' ' && c != '\u00A0') {
				return false;
			}
		}
		return true;
	}

	/** Cuts a cell down to its column, with an ellipsis so the cut is visible. */
	private static String trimTo(String cell, float width, boolean bold) {
		if (Widths.widthOf(cell, bold) <= width) {
			return cell;
		}
		float room = width - Widths.widthOf(ELLIPSIS, bold);
		StringBuilder out = new StringBuilder();
		float used = 0.0f;
		for (int i = 0; i < cell.length(); i++) {
			float advance = Widths.advance(cell.charAt(i), bold);
			if (used + advance > room) {
				break;
			}
			out.append(cell.charAt(i));
			used += advance;
		}
		return out + ELLIPSIS;
	}

	/**
	 * Blanks enough to walk from one place on the line to another.
	 *
	 * @return where the line actually reaches, which is the target itself unless the gap happens to
	 *         be one of the six widths a run of spaces cannot express
	 */
	private static float padTo(@Nullable Paragraph paragraph, float from, float to) {
		float gap = to - from;
		if (gap < Widths.space() - 0.001f) {
			return from;
		}
		Widths.Padding padding = Widths.pad(gap);
		if (paragraph != null) {
			int plain = padding.count() - padding.bold();
			if (plain > 0) {
				paragraph.insert(paragraph.length(), " ".repeat(plain), QuillStyle.PLAIN);
			}
			if (padding.bold() > 0) {
				paragraph.insert(paragraph.length(), " ".repeat(padding.bold()), QuillStyle.PLAIN.withBold(true));
			}
		}
		return from + padding.width();
	}
}
