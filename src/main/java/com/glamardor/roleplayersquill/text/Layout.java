package com.glamardor.roleplayersquill.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a page of paragraphs into the lines a book will show, and works out where each one sits.
 *
 * <p>This is the piece everything else leans on. The editor draws from it, so that what is on the
 * screen is what will be in the book; the encoder writes from it, so that the spaces it pads with
 * are the ones the layout asked for; the importer counts with it, to know when a page is full. One
 * measurement, three users, and no way for them to disagree.
 *
 * <p>The numbers are the game's: a page is 114 pixels wide and fourteen lines tall, because
 * {@code BookScreen} wraps at 114 and draws {@code min(128 / 9, lines)} of them.
 */
public final class Layout {
	public static final float PAGE_WIDTH = 114.0f;
	public static final int PAGE_LINES = 14;
	public static final int LINE_HEIGHT = 9;

	/** One press of the indent button, in spaces. */
	public static final int INDENT_SPACES = 2;

	private static final char HYPHEN = '-';

	private Layout() {
	}

	/**
	 * @param hyphenate       break words across lines so the text sits close
	 * @param justifyLastLine stretch the last line of a paragraph too, which typography says not to
	 * @param minBefore       letters that must stay on the line before a hyphen
	 * @param minAfter        letters that must be carried over after it
	 */
	public record Options(boolean hyphenate, boolean justifyLastLine, int minBefore, int minAfter) {
		public static final Options DEFAULT = new Options(false, false, 2, 2);
	}

	/** One line of a book page, with everything needed to draw it or to write it out. */
	public static final class LaidLine {
		/** Index of the paragraph this line came from. */
		public final int paragraph;
		/** First character of the line, as an index into that paragraph. */
		public final int start;
		/** One past the last character the line covers, trailing space included. */
		public final int end;
		/** One past the last character actually drawn: the same, with trailing space cut off. */
		public final int contentEnd;
		/** The list marker, drawn before the text; empty on every line but a list item's first. */
		public final String marker;
		public final QuillStyle markerStyle;
		/** The gap between the marker and the text it belongs to. */
		public Widths.Padding markerPad = Widths.Padding.NONE;
		/** The frame this line is written inside, if any: bars go on both ends of it. */
		public FrameStyle frame = FrameStyle.NONE;
		/** Where the line's leader stands, as an index into the paragraph, or -1 for none. */
		public int leaderAt = -1;
		/** How many dots the leader is, and the blanks that finish it off to the exact width. */
		public int leaderDots;
		public Widths.Padding leaderPad = Widths.Padding.NONE;

		/** How wide the leader is drawn, which is what the tab standing there costs the line. */
		public float leaderWidth() {
			return leaderDots * Widths.advance(LEADER_DOT, false) + leaderPad.width();
		}
		/** Whether a hyphen is drawn after the last character, because a word was broken here. */
		public final boolean hyphen;
		public final boolean firstOfParagraph;
		public final boolean lastOfParagraph;
		public final Alignment alignment;

		/** Pixels of blank before anything is drawn: the indent, the hanging indent, the alignment. */
		public Widths.Padding leftPad = Widths.Padding.NONE;
		/** Width of the line as written with single spaces, marker and hyphen included. */
		public float naturalWidth;
		/** Absolute indices of the space characters inside the line, in order. */
		public int[] spaces = new int[0];
		/** What each of those spaces is widened to, when the line is justified. */
		public Widths.Padding[] spacePads = new Widths.Padding[0];

		LaidLine(int paragraph, int start, int end, int contentEnd, String marker, QuillStyle markerStyle,
				boolean hyphen, boolean firstOfParagraph, boolean lastOfParagraph, Alignment alignment) {
			this.paragraph = paragraph;
			this.start = start;
			this.end = end;
			this.contentEnd = contentEnd;
			this.marker = marker;
			this.markerStyle = markerStyle;
			this.hyphen = hyphen;
			this.firstOfParagraph = firstOfParagraph;
			this.lastOfParagraph = lastOfParagraph;
			this.alignment = alignment;
		}

		public boolean justified() {
			return spacePads.length > 0;
		}

		/** The gap written in place of the space at this absolute index, or null when unchanged. */
		public Widths.Padding padFor(int spaceIndex) {
			for (int i = 0; i < spaces.length; i++) {
				if (spaces[i] == spaceIndex) {
					return spacePads[i];
				}
			}
			return null;
		}
	}

	// ---- laying out -----------------------------------------------------------------------------

	public static List<LaidLine> lay(List<Paragraph> page, Options options) {
		return lay(page, options, PAGE_WIDTH);
	}

	/**
	 * The same, to a width of your own choosing.
	 *
	 * <p>For laying a page into something narrower than a page: two columns side by side, each
	 * getting a little under half. Everything else about it is the same, which is the point – a
	 * column is a page that happens to be narrow.
	 */
	public static List<LaidLine> lay(List<Paragraph> page, Options options, float pageWidth) {
		List<LaidLine> lines = new ArrayList<>();
		int ordinal = 0;
		ListStyle running = ListStyle.NONE;
		for (int index = 0; index < page.size(); index++) {
			Paragraph paragraph = page.get(index);
			ListStyle style = paragraph.list();
			if (style == ListStyle.NONE) {
				ordinal = 0;
			} else if (paragraph.isContinuation()) {
				// The item was counted on the page before; counting it again would renumber the rest.
				ordinal = Math.max(ordinal, 1);
			} else if (style == running) {
				ordinal++;
			} else {
				ordinal = 1;
			}
			running = style;
			// A framed paragraph is laid into the room the bars leave it, and every line it makes
			// remembers that it is framed so the bars can be written on either end of it.
			FrameStyle frame = paragraph.frame();
			float room = frame.present() ? frame.inner() : pageWidth;
			int before = lines.size();
			layParagraph(lines, index, paragraph,
					paragraph.isContinuation() ? "" : style.marker(ordinal), style.marker(ordinal),
					options, room);
			for (int i = before; i < lines.size(); i++) {
				lines.get(i).frame = frame;
			}
		}
		return lines;
	}

	private static void layParagraph(List<LaidLine> out, int index, Paragraph paragraph, String marker,
			String hangMarker, Options options, float pageWidth) {
		int length = paragraph.length();
		float[] cumulative = cumulativeWidths(paragraph);
		float indent = paragraph.indent() * INDENT_SPACES * Widths.space();

		QuillStyle markerStyle = (length == 0 ? QuillStyle.PLAIN : paragraph.styleAt(0)).lookOnly();
		// The marker without the space that follows it in the list style. That space is worked out
		// here instead, because how wide it has to be is not a matter of taste – see hangingIndent.
		String glyph = trimEnd(marker);
		String hangGlyph = trimEnd(hangMarker);
		float glyphWidth = glyph.isEmpty() ? 0.0f : Widths.widthOf(glyph, markerStyle.bold());
		float hangWidth = hangGlyph.isEmpty() ? 0.0f
				: hangingIndent(Widths.widthOf(hangGlyph, markerStyle.bold()));
		Widths.Padding markerPad = glyph.isEmpty() ? Widths.Padding.NONE
				: Widths.pad(hangingIndent(glyphWidth) - glyphWidth);
		float markerWidth = glyphWidth + markerPad.width();
		float hyphenWidth = Widths.advance(HYPHEN, false);

		int cursor = 0;
		boolean first = true;
		while (true) {
			float hang = first && !marker.isEmpty() ? 0.0f : hangWidth;
			float available = pageWidth - indent - hang;
			float carried = first ? markerWidth : 0.0f;

			int end;
			boolean hyphen = false;
			int next;

			if (cursor >= length) {
				end = length;
				next = length;
			} else {
				int lastSpace = -1;
				int scan = cursor;
				float width = carried;
				while (scan < length) {
					char c = paragraph.charAt(scan);
					float advance = Widths.advance(c, paragraph.styleAt(scan).bold());
					if (width + advance > available && scan > cursor) {
						break;
					}
					if (c == ' ') {
						lastSpace = scan;
					}
					width += advance;
					scan++;
				}

				if (scan >= length) {
					end = length;
					next = length;
				} else {
					int cut = -1;
					if (options.hyphenate()) {
						cut = hyphenCut(paragraph, cumulative, cursor, scan, lastSpace, carried, available,
								hyphenWidth, options);
					}
					if (cut > cursor) {
						end = cut;
						next = cut;
						hyphen = true;
					} else if (lastSpace > cursor) {
						end = lastSpace;
						next = lastSpace + 1;
					} else if (lastSpace == cursor) {
						// The line begins with the only space it has room for; keep going past it.
						end = cursor + 1;
						next = cursor + 1;
					} else {
						end = Math.max(scan, cursor + 1);
						next = end;
					}

					// What is left may be nothing but blanks that are about to be trimmed away. Going
					// round again for them produces an empty line the reader will not have, which
					// puts every line after it one place out and can push the last one off the page.
					if (next < length && onlyDroppableBlanks(paragraph, next, length)) {
						end = length;
						next = length;
					}
				}
			}

			int contentEnd = end;
			// Trailing blanks are dropped, because they only cost characters and push alignment
			// about. Except when something has been drawn on them: an underlined run of spaces is
			// a horizontal rule, and a rule is nothing but trailing blanks.
			while (contentEnd > cursor && paragraph.charAt(contentEnd - 1) == ' '
					&& !paragraph.styleAt(contentEnd - 1).marksBlanks()) {
				contentEnd--;
			}

			boolean last = next >= length;
			LaidLine line = new LaidLine(index, cursor, end, contentEnd,
					first ? glyph : "", markerStyle, hyphen, first, last, paragraph.alignment());
			if (first && !glyph.isEmpty()) {
				line.markerPad = markerPad;
			}
			finish(line, paragraph, cumulative, indent + hang, available, carried, markerWidth, hyphenWidth, options);
			out.add(line);

			if (last) {
				break;
			}
			cursor = next;
			first = false;
		}
	}

	/**
	 * The best place to break the word the line ran out of room in, or -1 for none.
	 *
	 * <p>Takes the largest hyphen point that still fits with the hyphen itself drawn, which is the
	 * whole point of hyphenating: fill the line.
	 */
	private static int hyphenCut(Paragraph paragraph, float[] cumulative, int lineStart, int overflow,
			int lastSpace, float carried, float available, float hyphenWidth, Options options) {
		int wordStart = Math.max(lineStart, lastSpace + 1);
		if (wordStart >= overflow) {
			return -1;
		}
		int wordEnd = overflow;
		while (wordEnd < paragraph.length() && paragraph.charAt(wordEnd) != ' ') {
			wordEnd++;
		}
		String word = paragraph.text().substring(wordStart, wordEnd);
		if (!isHyphenable(word)) {
			return -1;
		}
		List<Integer> points = Hyphenator.points(word, options.minBefore(), options.minAfter());
		int best = -1;
		for (int point : points) {
			int cut = wordStart + point;
			if (cut <= lineStart) {
				continue;
			}
			float width = carried + cumulative[cut] - cumulative[lineStart] + hyphenWidth;
			if (width <= available) {
				best = cut;
			}
		}
		return best;
	}

	/** True when everything in the range is a blank that carries no mark of its own. */
	private static boolean onlyDroppableBlanks(Paragraph paragraph, int from, int to) {
		for (int i = from; i < to; i++) {
			if (paragraph.charAt(i) != ' ' || paragraph.styleAt(i).marksBlanks()) {
				return false;
			}
		}
		return true;
	}

	/** Only words: a break inside a number or a mixture of letters and punctuation reads as an error. */
	private static boolean isHyphenable(String word) {
		if (word.length() < 4) {
			return false;
		}
		for (int i = 0; i < word.length(); i++) {
			if (!Character.isLetter(word.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/** How far in a list item's text starts, marker and gap together. For reading a book back. */
	public static float hangingIndentOf(String marker) {
		String glyph = trimEnd(marker);
		return glyph.isEmpty() ? 0.0f : hangingIndent(Widths.widthOf(glyph, false));
	}

	/** The marker without the blank the list style puts after it. */
	private static String trimEnd(String marker) {
		int end = marker.length();
		while (end > 0 && marker.charAt(end - 1) == ' ') {
			end--;
		}
		return marker.substring(0, end);
	}

	/**
	 * How far in from the margin a list item's text starts.
	 *
	 * <p>Not simply "the marker and a space". The second line of an item reaches that point by
	 * writing spaces, and spaces are four pixels or five, so a bullet three pixels wide followed by
	 * a space – seven in all – is a distance the second line cannot write. It lands two pixels short
	 * and the item comes out with a bent left edge, which is what a numbered list happens to avoid
	 * only because "1." and a space add up to twelve.
	 *
	 * <p>So the gap is chosen rather than assumed: the nearest distance that both the second line
	 * can reach on its own and the first line can reach from the end of its marker. For a bullet
	 * that is eight – the second line writes two spaces, the first writes one bold one.
	 */
	private static float hangingIndent(float glyphWidth) {
		float space = Widths.space();
		for (float indent = glyphWidth + space; indent <= glyphWidth + space + 16.0f; indent += 1.0f) {
			if (expressible(indent) && expressible(indent - glyphWidth)) {
				return indent;
			}
		}
		return glyphWidth + space;
	}

	/** Whether a run of spaces can be exactly this wide. */
	private static boolean expressible(float width) {
		return width < 0.001f || Math.abs(Widths.pad(width).width() - width) < 0.001f;
	}

	/** Works out the padding: what goes in front of the line and, when justified, between its words. */
	private static void finish(LaidLine line, Paragraph paragraph, float[] cumulative, float prefix,
			float available, float carried, float markerWidth, float hyphenWidth, Options options) {
		float text = cumulative[line.contentEnd] - cumulative[line.start];
		float natural = carried + text + (line.hyphen ? hyphenWidth : 0.0f);
		line.naturalWidth = natural;

		boolean justify = line.alignment == Alignment.JUSTIFY
				&& (!line.lastOfParagraph || options.justifyLastLine() || paragraph.continues())
				&& !line.hyphen;

		if (justify) {
			List<Integer> spaceList = new ArrayList<>();
			for (int i = line.start; i < line.contentEnd; i++) {
				if (paragraph.charAt(i) == ' ') {
					spaceList.add(i);
				}
			}
			if (!spaceList.isEmpty() && spread(line, paragraph, spaceList, natural, available)) {
				line.leftPad = Widths.pad(prefix);
				return;
			}
		}

		float slack = Math.max(0.0f, available - natural);

		// A leader eats the slack before anything else can: that is the whole of what it is for.
		// "Меч" and "30 монет" on one line with the dots between them reaching from one to the other.
		for (int i = line.start; i < line.contentEnd; i++) {
			if (paragraph.charAt(i) != Widths.LEADER) {
				continue;
			}
			line.leaderAt = i;
			fillLeader(line, slack);
			line.naturalWidth = natural + line.leaderWidth();
			line.leftPad = Widths.pad(prefix);
			return;
		}

		float offset = switch (line.alignment) {
			case CENTER -> slack / 2.0f;
			case RIGHT -> slack;
			default -> 0.0f;
		};
		line.leftPad = Widths.pad(prefix + offset);
	}

	/** The character the dots are made of. */
	private static final char LEADER_DOT = '.';

	/**
	 * Fills a leader with as many dots as the gap holds, and blanks for whatever is left over.
	 *
	 * <p>A dot is two pixels and a space is four or five, so between them almost any width can be
	 * written exactly; where it cannot, a dot is given up and the blanks take its place. The blanks
	 * go first so that the dots finish flush against the text on the right, which is how a leader in
	 * a table of contents is set.
	 */
	private static void fillLeader(LaidLine line, float gap) {
		float dot = Widths.advance(LEADER_DOT, false);
		if (gap < dot || dot <= 0.0f) {
			line.leaderDots = 0;
			line.leaderPad = Widths.Padding.NONE;
			return;
		}
		for (int dots = (int) Math.floor(gap / dot); dots >= 0; dots--) {
			float rest = gap - dots * dot;
			Widths.Padding pad = rest < 0.001f ? Widths.Padding.NONE : Widths.pad(rest);
			if (Math.abs(pad.width() - rest) < 0.001f) {
				line.leaderDots = dots;
				line.leaderPad = pad;
				return;
			}
		}
		line.leaderDots = (int) Math.floor(gap / dot);
		line.leaderPad = Widths.Padding.NONE;
	}

	/**
	 * Shares the slack out between the words.
	 *
	 * <p>Every gap is a run of spaces, and every space is four pixels or five. So the whole run
	 * across the line is {@code 4n + b} pixels for some {@code n} spaces of which {@code b} are
	 * bold, and {@code b} is never more than three – which is why a justified line costs a handful
	 * of characters rather than a bold marker on every gap.
	 *
	 * @return false when the line is already too full to justify, in which case it is left alone
	 */
	private static boolean spread(LaidLine line, Paragraph paragraph, List<Integer> spaces, float natural,
			float available) {
		float unit = Widths.space();
		float gain = Widths.boldGain();
		if (unit <= 0.0f) {
			return false;
		}
		int gaps = spaces.size();
		float fixed = natural - gaps * unit;
		float room = available - fixed;
		int total = (int) Math.floor((room + 0.001f) / unit);
		if (total < gaps) {
			return false;
		}
		int bold = 0;
		if (gain > 0.0f) {
			bold = Math.min(total, (int) Math.floor((room - total * unit + 0.001f) / gain));
		}

		int[] counts = new int[gaps];
		int base = total / gaps;
		int extra = total % gaps;
		for (int i = 0; i < gaps; i++) {
			// The wider gaps go on the right, where a reader notices them least.
			counts[i] = base + (i >= gaps - extra ? 1 : 0);
		}
		int[] bolds = new int[gaps];
		for (int i = 0, at = 0; i < bold; i++) {
			int guard = 0;
			while (bolds[at] >= counts[at] && guard++ < gaps) {
				at = (at + 1) % gaps;
			}
			bolds[at]++;
			at = (at + 1) % gaps;
		}

		line.spaces = new int[gaps];
		line.spacePads = new Widths.Padding[gaps];
		for (int i = 0; i < gaps; i++) {
			line.spaces[i] = spaces.get(i);
			line.spacePads[i] = new Widths.Padding(counts[i], bolds[i], counts[i] * unit + bolds[i] * gain);
		}
		return true;
	}

	/** Running width of the paragraph, so any slice of it can be measured by subtraction. */
	private static float[] cumulativeWidths(Paragraph paragraph) {
		float[] cumulative = new float[paragraph.length() + 1];
		for (int i = 0; i < paragraph.length(); i++) {
			cumulative[i + 1] = cumulative[i] + Widths.advance(paragraph.charAt(i), paragraph.styleAt(i).bold());
		}
		return cumulative;
	}

	// ---- measuring a laid-out line --------------------------------------------------------------

	/** Where a character sits across the page, measured from the left edge of the text area. */
	public static float xOf(LaidLine line, Paragraph paragraph, int index) {
		float x = line.frame.textLeft() + line.leftPad.width();
		if (!line.marker.isEmpty()) {
			x += Widths.widthOf(line.marker, line.markerStyle.bold()) + line.markerPad.width();
		}
		for (int i = line.start; i < Math.min(index, line.contentEnd); i++) {
			char c = paragraph.charAt(i);
			if (i == line.leaderAt) {
				x += line.leaderWidth();
				continue;
			}
			Widths.Padding pad = c == ' ' ? line.padFor(i) : null;
			x += pad != null ? pad.width() : Widths.advance(c, paragraph.styleAt(i).bold());
		}
		// Past the drawn text, over the trailing blanks the line cut off. Nothing is drawn there, but
		// the caret still belongs after them: a space typed at the end of a line is a space, and a
		// caret that does not move when you press the space bar looks like a key that did not work.
		for (int i = line.contentEnd; i < Math.min(index, line.end); i++) {
			x += Widths.advance(paragraph.charAt(i), paragraph.styleAt(i).bold());
		}
		return x;
	}

	/** The character a click at this offset lands on, clamped to the line. */
	public static int indexAt(LaidLine line, Paragraph paragraph, float x) {
		float at = line.frame.textLeft() + line.leftPad.width();
		if (!line.marker.isEmpty()) {
			at += Widths.widthOf(line.marker, line.markerStyle.bold()) + line.markerPad.width();
		}
		if (x <= at) {
			return line.start;
		}
		for (int i = line.start; i < line.contentEnd; i++) {
			char c = paragraph.charAt(i);
			Widths.Padding pad = c == ' ' ? line.padFor(i) : null;
			float advance = i == line.leaderAt ? line.leaderWidth()
					: pad != null ? pad.width() : Widths.advance(c, paragraph.styleAt(i).bold());
			if (x < at + advance / 2.0f) {
				return i;
			}
			at += advance;
		}
		return line.contentEnd;
	}
}
