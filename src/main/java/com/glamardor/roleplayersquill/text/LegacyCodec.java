package com.glamardor.roleplayersquill.text;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes a page out as the string a vanilla server will accept, and reads one back in.
 *
 * <p>A book leaves the client as plain text: {@code BookUpdateC2SPacket} carries strings, and the
 * server turns them into components itself. The one thing it does not do on the way is strip
 * section signs, which is what makes all of this possible – the same {@code §} codes the chat is
 * kicked for carrying are passed through for books untouched, and the book renderer resolves them
 * exactly as it resolves the ones in a sign.
 *
 * <p>So everything the editor can do to a page has to come out as codes and spaces. The codes carry
 * the five switches and sixteen colours; the spaces carry the alignment, four pixels at a time and
 * five when they are bold. Anything left over – a real colour, a link, a tooltip – needs the page
 * to be sent as a component instead, and that is a different writer.
 *
 * <h2>Being frugal</h2>
 *
 * <p>A page holds 1024 characters and every {@code §x} spends two of them, so the codes emitted are
 * the fewest that get from the style now in force to the one wanted. A colour code resets the
 * switches, which means turning bold off is spelled either as the colour again or as {@code §r},
 * and picking the shorter of the two is worth a line of text per page on a heavily formatted one.
 *
 * <h2>Plain is not black</h2>
 *
 * <p>Inside a book the two are the same thing: the ink a page is written in is black, so text with
 * no colour of its own and text coloured {@code §0} come out identical. Elsewhere they are not. The
 * server's torn-page plugin lifts what is written here onto an item, where the default ink is the
 * pale grey of a tooltip – and a page that said {@code §0} to mean "nothing" arrives there as black
 * on black. So text the author left alone has to leave here having said nothing about its colour.
 */
public final class LegacyCodec {
	public static final char SECTION = '§';
	private static final char[] COLOR_CODES = {
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
	};

	private LegacyCodec() {
	}

	/** Hover text is written by hand with codes in it, and the book renderer resolves them itself. */
	public static Text toText(String legacy) {
		return Text.literal(legacy);
	}

	// ---- writing --------------------------------------------------------------------------------

	/**
	 * One page, laid out and written.
	 *
	 * @param page  the paragraphs, as the editor holds them
	 * @param lines the lines {@link Layout} broke them into, already padded
	 */
	public static String encode(List<Paragraph> page, List<Layout.LaidLine> lines) {
		Writer written = write(page, lines, false);
		if (written.inked() == 0) {
			return written.toString();
		}
		// Somewhere on this page "no formatting at all" had to be spelled as black ink, because §r
		// would have meant something else where it stood. Written again, this time spending two
		// characters at the end of every line that would otherwise hand its formatting on, §r means
		// what it says and the ink is not needed. Worth doing only where it buys something, which is
		// why the frugal attempt comes first.
		Writer clean = write(page, lines, true);
		return clean.inked() < written.inked() ? clean.toString() : written.toString();
	}

	private static Writer write(List<Paragraph> page, List<Layout.LaidLine> lines, boolean keepResetPlain) {
		Writer writer = new Writer(keepResetPlain);
		int last = worthWriting(lines);
		int at = 0;
		boolean started = false;
		while (at < last) {
			// One paragraph at a time, because whether it needs line breaks written into it is a
			// property of the paragraph and not of any one of its lines.
			int index = lines.get(at).paragraph;
			int after = at;
			while (after < last && lines.get(after).paragraph == index) {
				after++;
			}
			if (started) {
				writer.newLine();
			}
			started = true;

			if (needsNoPixels(page, lines, at, after)) {
				// Written the way somebody without this mod would have written it: straight through,
				// with the break at the end of the paragraph and nowhere else, because the game wraps
				// it at the same places this mod just laid it out at.
				//
				// A line break at the end of every line is this mod's signature, and things that read
				// a page rather than draw it can see it. The server's torn-page plugin turns each one
				// into a line of its own, so a page written here came out double spaced while the same
				// page typed by anybody else came out right.
				encodeStraight(writer, page.get(index), lines, at, after);
			} else {
				for (int i = at; i < after; i++) {
					if (i > at) {
						writer.newLine();
					}
					encodeLine(writer, page, lines.get(i));
				}
			}
			at = after;
		}
		return writer;
	}

	/**
	 * How many of the laid lines are worth putting on the page.
	 *
	 * <p>Empty lines hanging off the end are not written. Nothing is drawn on them, so the reader
	 * never knew they were there – but the vanilla editor is a text box that counts lines rather than
	 * a page that draws them, and it is built with room for exactly fourteen. A page of fourteen
	 * lines followed by an empty one is fifteen lines to it: it grows a scrollbar, the text slides
	 * under itself, and that is what somebody without this mod opens the book to.
	 */
	public static int worthWriting(List<Layout.LaidLine> lines) {
		int last = lines.size();
		while (last > 0 && isBlank(lines.get(last - 1))) {
			last--;
		}
		return last;
	}

	/**
	 * Whether a paragraph is laid out the way the game would lay it out by itself.
	 *
	 * <p>Nothing pushed anywhere: no alignment, no indent, no marker, no frame, no leader, no gap
	 * widened to reach the margin and no word broken with a hyphen. Such a paragraph needs no line
	 * breaks written into it, and writing them anyway is the difference between a book that is
	 * ordinary and a book that only looks ordinary.
	 */
	public static boolean needsNoPixels(List<Paragraph> page, List<Layout.LaidLine> lines, int from, int to) {
		for (int i = from; i < to; i++) {
			Layout.LaidLine line = lines.get(i);
			if (!line.leftPad.isEmpty() || line.justified() || !line.marker.isEmpty()
					|| line.frame.present() || line.leaderAt >= 0 || line.hyphen) {
				return false;
			}
		}
		Paragraph paragraph = page.get(lines.get(from).paragraph);
		return !holdsWordsTogether(paragraph) || gameBreaksWhereWeDid(paragraph, lines, from, to);
	}

	/** Whether anything in this paragraph is a blank the reader must not have a line break at. */
	private static boolean holdsWordsTogether(Paragraph paragraph) {
		return paragraph.text().indexOf(Widths.NOBREAK) >= 0;
	}

	/**
	 * Whether the game, left to wrap this paragraph itself, would break it exactly where we did.
	 *
	 * <p>Asked only of a paragraph holding an unbreakable blank, because that is the only thing that
	 * can make the two disagree. The blank goes onto the page as an ordinary space – there is nothing
	 * else four pixels wide that a book can hold – and the game is perfectly willing to end a line at
	 * it. Usually it has no reason to: the pair sits in the middle of a line and the break falls
	 * somewhere else entirely, and then the paragraph can still be written straight through, which is
	 * what keeps a torn-out page reading as one paragraph rather than as a column of lines.
	 *
	 * <p>Where it would disagree, the answer is no and the breaks are written out. That costs this
	 * paragraph the plain writing and keeps the pair together, which is what was asked for.
	 *
	 * <p>The wrapping simulated here is {@code TextHandler.LineBreakingVisitor}'s, the same one
	 * {@link Layout} follows: a blank is noted as a place to end before the width is tested.
	 */
	private static boolean gameBreaksWhereWeDid(Paragraph paragraph, List<Layout.LaidLine> lines,
			int from, int to) {
		int length = paragraph.length();
		int cursor = lines.get(from).start;
		int line = from;
		while (true) {
			int lastBlank = -1;
			int scan = cursor;
			float width = 0.0f;
			while (scan < length) {
				char c = paragraph.charAt(scan);
				float advance = Widths.advance(c, paragraph.styleAt(scan).bold());
				if (c == ' ' || c == Widths.NOBREAK) {
					lastBlank = scan;
				}
				if (width + advance > Layout.PAGE_WIDTH && scan > cursor) {
					break;
				}
				width += advance;
				scan++;
			}
			if (scan >= length) {
				return line == to - 1;
			}
			int next = lastBlank > cursor ? lastBlank + 1
					: lastBlank == cursor ? cursor + 1
					: Math.max(scan, cursor + 1);
			line++;
			if (line >= to || lines.get(line).start != next) {
				return false;
			}
			cursor = next;
		}
	}

	/**
	 * A paragraph written as one run of text, with its codes and without a break inside it.
	 *
	 * <p>The breaks are still there – the game puts them in as it draws, at the places this mod has
	 * just laid the paragraph out at, which is the whole reason it may be written this way. They are
	 * pointed out to the writer as it passes them, because what is in force at a break is what
	 * {@code §r} means on the far side of it.
	 *
	 * <p>And usually they can be disarmed. A paragraph breaks at a blank, and the blank it breaks at
	 * is thrown away – the game draws neither its ink nor its width. Its formatting is therefore
	 * free, and a blank written plain hands nothing over the break, which leaves {@code §r} meaning
	 * what it says for the whole page below. Not always free, mind: a bold blank is a pixel wider
	 * than a plain one, and one that is underlined or struck through is drawn after all.
	 */
	private static void encodeStraight(Writer writer, Paragraph paragraph,
			List<Layout.LaidLine> lines, int at, int after) {
		int to = lines.get(after - 1).contentEnd;
		int next = at + 1;
		for (int i = lines.get(at).start; i < to; i++) {
			if (next < after && i == lines.get(next).start) {
				writer.noteWrap();
				next++;
			}
			QuillStyle style = paragraph.styleAt(i);
			if (next < after && i >= lines.get(next - 1).contentEnd && writer.wouldSayPlain()
					&& !style.bold() && !style.marksBlanks()) {
				style = QuillStyle.PLAIN;
			}
			writer.style(style);
			writer.raw(String.valueOf(paragraph.charAt(i)));
		}
	}

	/** Whether this line puts nothing at all on the page. */
	private static boolean isBlank(Layout.LaidLine line) {
		return line.contentEnd <= line.start && line.marker.isEmpty() && !line.frame.present();
	}

	/**
	 * How many lines the vanilla book editor makes of a page.
	 *
	 * <p>Not the same question as how many lines a reader sees. The reader gets a page that is drawn:
	 * an empty line at the bottom draws nothing and costs nothing. Anybody opening an unsigned book
	 * without this mod gets {@code BookEditScreen}, which is a text box built with room for exactly
	 * fourteen lines – and a text box counts lines, empty or not. One line too many and it grows a
	 * scrollbar and slides the text under itself.
	 *
	 * <p>Every explicit break is a line, and what lies between two of them is wrapped the way the
	 * game wraps it: greedily, at the last blank that still fits.
	 */
	public static int editorLines(String page) {
		int total = 0;
		for (String segment : page.split("\n", -1)) {
			total += wrappedLines(segment);
		}
		return total;
	}

	/** Whether a page as it stands can be opened without this mod and look the way it was meant to. */
	public static boolean fitsTheVanillaEditor(String page) {
		return editorLines(page) <= Layout.PAGE_LINES;
	}

	/**
	 * One stretch between two explicit breaks, wrapped the way the game wraps it.
	 *
	 * <p>Transcribed from {@code TextHandler.LineBreakingVisitor}: a blank is noted as a place the
	 * line may end <em>before</em> the width is tested, the width of every character including the
	 * blank is counted, and a line that goes over ends at the last blank noted – or, if there was
	 * none, at the character that went over.
	 */
	private static int wrappedLines(String segment) {
		int lines = 1;
		float total = 0.0f;
		float beforeSpace = 0.0f;
		float spaceWidth = 0.0f;
		boolean haveSpace = false;
		boolean nonEmpty = false;
		boolean bold = false;
		for (int i = 0; i < segment.length(); i++) {
			char c = segment.charAt(i);
			if (c == SECTION) {
				if (i + 1 < segment.length()) {
					bold = applyCode(QuillStyle.PLAIN.withBold(bold),
							Character.toLowerCase(segment.charAt(i + 1))).bold();
					i++;
				}
				continue;
			}
			float advance = Widths.advance(c, bold);
			if (c == ' ') {
				haveSpace = true;
				beforeSpace = total;
				spaceWidth = advance;
			}
			total += advance;
			if (nonEmpty && total > Layout.PAGE_WIDTH) {
				lines++;
				// What is left over is whatever followed the blank the line ended at. With no blank
				// to end at, the line ends at this character and this character starts the next one.
				total = haveSpace ? total - beforeSpace - spaceWidth : advance;
				haveSpace = false;
				nonEmpty = total != 0.0f;
				continue;
			}
			nonEmpty |= advance != 0.0f;
		}
		return lines;
	}

	private static void encodeLine(Writer writer, List<Paragraph> page, Layout.LaidLine line) {
		Paragraph paragraph = page.get(line.paragraph);

		if (line.frame.present()) {
			writer.style(QuillStyle.PLAIN);
			writer.raw(String.valueOf(line.frame.bar));
			Widths.Padding gap = Widths.pad(FrameStyle.LEFT_MARGIN);
			writer.spaces(QuillStyle.PLAIN, gap.count(), gap.bold());
		}
		if (!line.leftPad.isEmpty()) {
			writer.spaces(QuillStyle.PLAIN, line.leftPad.count(), line.leftPad.bold());
		}
		if (!line.marker.isEmpty()) {
			writer.style(line.markerStyle);
			writer.raw(line.marker);
			if (!line.markerPad.isEmpty()) {
				writer.spaces(QuillStyle.PLAIN, line.markerPad.count(), line.markerPad.bold());
			}
		}

		for (int i = line.start; i < line.contentEnd; i++) {
			char c = paragraph.charAt(i);
			QuillStyle style = paragraph.styleAt(i);
			if (i == line.leaderAt) {
				// The tab itself is never written. What goes on the page is what it stood for.
				if (!line.leaderPad.isEmpty()) {
					writer.spaces(style.withObfuscated(false), line.leaderPad.count(), line.leaderPad.bold());
				}
				if (line.leaderDots > 0) {
					writer.style(style);
					writer.raw(".".repeat(line.leaderDots));
				}
				continue;
			}
			if (c == ' ') {
				Widths.Padding pad = line.padFor(i);
				if (pad != null && (pad.count() != 1 || pad.bold() != 0)) {
					// A widened gap: the obfuscation has to come off or the blank fills with noise.
					writer.spaces(style.withObfuscated(false), pad.count(), pad.bold());
					continue;
				}
			}
			writer.style(style);
			writer.raw(String.valueOf(c));
		}

		if (line.hyphen) {
			writer.style(paragraph.styleAt(Math.max(line.start, line.contentEnd - 1)));
			writer.raw("-");
		}

		if (line.frame.present()) {
			// Out to the bar on the right, which stands in the same place on every line: there are
			// always twelve pixels or more to cross, and every such gap can be written with spaces.
			float used = line.frame.textLeft() + line.leftPad.width() + line.naturalWidth;
			Widths.Padding gap = Widths.pad(line.frame.barRight() - used);
			writer.spaces(QuillStyle.PLAIN, gap.count(), gap.bold());
			writer.style(QuillStyle.PLAIN);
			writer.raw(String.valueOf(line.frame.bar));
		}
	}

	/** What a page will cost of its thousand characters, without building the page twice. */
	public static int cost(List<Paragraph> page, List<Layout.LaidLine> lines) {
		return encode(page, lines).length();
	}

	/** The state machine that keeps the codes down to the ones that change something. */
	public static final class Writer {
		private final StringBuilder out = new StringBuilder();
		/** Whether to spend two characters at a line break to keep {@code §r} meaning what it says. */
		private final boolean keepResetPlain;
		private int color = -1;
		private boolean bold;
		private boolean italic;
		private boolean underlined;
		private boolean strikethrough;
		private boolean obfuscated;
		/** Whether {@code §r} written here would mean plain. True at the top of a page. */
		private boolean resetIsPlain = true;
		/** How often plain text had to be written as black ink instead. */
		private int inked;

		public Writer() {
			this(false);
		}

		public Writer(boolean keepResetPlain) {
			this.keepResetPlain = keepResetPlain;
		}

		/** How often this page had to say "black" where it meant "nothing". */
		public int inked() {
			return inked;
		}

		/**
		 * Writes text, never a bare section sign.
		 *
		 * <p>One that reached the page would be read as the start of a code and would swallow the
		 * character after it – which is how a page loses a line break and a book comes back looking
		 * like a different book. The editor refuses to type one, but a document read from an older
		 * draft or imported from a file can still be carrying one, and this is the last place to
		 * catch it.
		 *
		 * <p>An unbreakable blank goes down as an ordinary space, which is all a book can hold. What
		 * made it unbreakable was decided before this: the paragraph it stands in was written with
		 * its own line breaks, so the game never gets the chance to end a line there.
		 */
		public void raw(String text) {
			if (text.indexOf(SECTION) < 0 && text.indexOf(Widths.NOBREAK) < 0) {
				out.append(text);
				return;
			}
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				if (c == Widths.NOBREAK) {
					out.append(' ');
				} else if (c != SECTION) {
					out.append(c);
				}
			}
		}

		/**
		 * Ends a line, and with it settles what {@code §r} will mean on the next one.
		 *
		 * <p>Whatever is in force at a break is handed to the rest of the page as its reset style, so
		 * a line that ends in the middle of a colour takes {@code §r} away from every line after it.
		 * Two characters spent here buy it back – but only on a page that was shown to need it, and
		 * only where {@code §r} still means plain, since that is the only thing that can undo it.
		 */
		public void newLine() {
			if (keepResetPlain && resetIsPlain && !isPlain()) {
				code('r');
				clearSwitches();
				color = -1;
			}
			out.append('\n');
			resetIsPlain = isPlain();
		}

		/**
		 * A break the game will make by itself, in the middle of a paragraph written straight through.
		 *
		 * <p>Taken only as bad news. Where the style at such a break is plain the reset style is left
		 * as it was rather than called plain again: this mod's idea of where the game wraps is a very
		 * good one, but {@code §r} is not a thing to spend on a very good idea, and a break that is
		 * really a line or two along would otherwise put the colour back rather than take it off.
		 */
		public void noteWrap() {
			resetIsPlain = resetIsPlain && isPlain();
		}

		/** Whether plain text written here and now would come out saying nothing about its colour. */
		public boolean wouldSayPlain() {
			return keepResetPlain && resetIsPlain;
		}

		/** Whether nothing at all is in force: no switch, and no colour code written yet. */
		private boolean isPlain() {
			return color < 0 && !bold && !italic && !underlined && !strikethrough && !obfuscated;
		}

		/**
		 * Moves the active formatting to this style, writing as little as will do it.
		 *
		 * <h2>Why {@code §r} cannot simply be written</h2>
		 *
		 * <p>Because {@code §r} does not mean "plain". It means "back to the style this piece of
		 * text started in", and once a page has been wrapped that is not plain at all:
		 * {@code TextHandler.collectLine} hands the rest of the page on carrying whatever style was
		 * in force at the line break, and {@code Language.reorder} then renders that remainder with
		 * its reset style set to the very same thing. So a {@code §r} at the start of the line after
		 * a horizontal rule put the underline and the bold back rather than taking them off.
		 *
		 * <p>Worse, it only did so when drawing. The measuring pass resets to nothing, so the widths
		 * were right while the picture was wrong, which is why the editor and the book disagreed
		 * without either of them being able to notice.
		 *
		 * <p>A colour code has no such problem: it sets the style outright, switches and all. Black
		 * is what a book is written in anyway, so turning formatting off that way costs exactly what
		 * {@code §r} costs and always means something.
		 *
		 * <h2>Why there is one after all</h2>
		 *
		 * <p>Because black is a colour, and a page does leave the book: the torn-page plugin puts
		 * what is written here into an item's lore, where the ink is pale grey and black is
		 * unreadable. Only {@code §r} says "no colour", so it is used wherever it is safe – which is
		 * wherever the reset style at that point is known to be plain, and that is something this
		 * writer can know, because it is the one putting the breaks in. Where it is not safe, black
		 * ink is still the answer, and {@link #encode} notices and tries the page again.
		 */
		public void style(QuillStyle target) {
			int index = target.legacyColorIndex();
			// No colour of its own means the ink the book is written in, and that is black.
			int want = index < 0 ? 0 : index;
			boolean removing = bold && !target.bold()
					|| italic && !target.italic()
					|| underlined && !target.underlined()
					|| strikethrough && !target.strikethrough()
					|| obfuscated && !target.obfuscated();
			// Nothing has been written yet and nothing is wanted: a page of plain text starts with
			// no codes at all, as it always did.
			boolean untouched = color == -1 && want == 0;
			// Not "black text", which somebody may well have asked for, but text with no colour of
			// its own – the one thing a § code cannot name and §r can. The switches are no business
			// of this: a bold blank with no colour is as much "no colour" as a letter is, and it is
			// the commonest of the lot, since that is what a list marker is padded with.
			boolean inkless = index < 0;

			if (removing || (want != color && !untouched)) {
				if (inkless && resetIsPlain) {
					code('r');
					color = -1;
				} else {
					if (inkless) {
						inked++;
					}
					code(COLOR_CODES[want]);
					color = want;
				}
				clearSwitches();
			}

			if (target.bold() && !bold) {
				code('l');
				bold = true;
			}
			if (target.strikethrough() && !strikethrough) {
				code('m');
				strikethrough = true;
			}
			if (target.underlined() && !underlined) {
				code('n');
				underlined = true;
			}
			if (target.italic() && !italic) {
				code('o');
				italic = true;
			}
			if (target.obfuscated() && !obfuscated) {
				code('k');
				obfuscated = true;
			}
		}

		/**
		 * A run of blanks, some of them bold so the run lands on the pixel it was asked for.
		 *
		 * <p>The plain ones go first: that way {@code §l} is written once, at the end, and whatever
		 * follows has to turn bold off anyway.
		 */
		public void spaces(QuillStyle base, int count, int boldCount) {
			int plain = Math.max(0, count - boldCount);
			QuillStyle flat = base.withObfuscated(false).withBold(false);
			if (plain > 0) {
				style(flat);
				out.append(" ".repeat(plain));
			}
			if (boldCount > 0) {
				style(flat.withBold(true));
				out.append(" ".repeat(boldCount));
			}
		}

		private void clearSwitches() {
			bold = false;
			italic = false;
			underlined = false;
			strikethrough = false;
			obfuscated = false;
		}

		private void code(char c) {
			out.append(SECTION).append(c);
		}

		public int length() {
			return out.length();
		}

		@Override
		public String toString() {
			return out.toString();
		}
	}

	// ---- reading --------------------------------------------------------------------------------

	/**
	 * A page written by anything – this mod, Stendhal, a datapack, a player typing codes by hand –
	 * read back into paragraphs.
	 *
	 * <p>Every line break becomes a paragraph, because that is all a string of text can tell us: the
	 * difference between a paragraph and a line the book happened to wrap was lost the moment the
	 * page was written. Where a run of leading spaces looks like an alignment, it is read as one,
	 * so that a centred title comes back centred rather than as a heap of blanks the player has to
	 * count.
	 */
	public static List<Paragraph> decode(String page) {
		List<Paragraph> paragraphs = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		List<QuillStyle> styles = new ArrayList<>();
		QuillStyle style = QuillStyle.PLAIN;

		// Read exactly the way TextVisitFactory.visitFormatted reads, because that is what the book
		// renderer uses and anything else is a different book. Two things fall out of that and both
		// of them matter:
		//
		//   · a section sign always swallows the character after it, valid code or not – so a page
		//     ending a line with a stray §, which is easily done by hand and which Stendhal leaves
		//     behind, has its line break eaten and runs on into the next line. Splitting on newlines
		//     first and reading the codes afterwards gets a page with one line too many, warns that
		//     it will not fit, and is wrong about where every word goes.
		//   · formatting carries across a line break. The vanilla editor's text box resets at every
		//     one, which is why a book can look different there than it does when it is read; the
		//     reader is the one to agree with.
		for (int i = 0; i < page.length(); i++) {
			char c = page.charAt(i);
			if (c == SECTION) {
				if (i + 1 >= page.length()) {
					break;
				}
				style = applyCode(style, Character.toLowerCase(page.charAt(i + 1)));
				i++;
				continue;
			}
			if (c == '\n') {
				paragraphs.add(finishLine(text, styles));
				continue;
			}
			text.append(c);
			styles.add(style);
		}
		paragraphs.add(finishLine(text, styles));
		restoreLists(paragraphs);
		return paragraphs;
	}

	/**
	 * Reads a list back out of the marks it left on the page.
	 *
	 * <p>A list is a property of a paragraph, and a page is a string: by the time a book has been
	 * written and opened again, all that is left of a list is the bullets and numbers standing in
	 * the text. They still look right, but they are no longer a list – pressing return at the end of
	 * an item starts an ordinary line, which is what this is here to stop.
	 *
	 * <p>Read carefully, and deliberately not at all in the doubtful cases. A bullet is unambiguous:
	 * nobody types one except to make a list. A row of numbers is taken only when it starts at one
	 * and counts up without a gap, so that reading it and writing it back cannot renumber anything.
	 * A dash is never taken, however much it looks like a list – in Russian a line opening with a
	 * dash is almost always speech, and turning every line of dialogue into a bulleted list would be
	 * a far worse bug than the one being fixed.
	 */
	public static void restoreLists(List<Paragraph> paragraphs) {
		for (Paragraph paragraph : paragraphs) {
			if (paragraph.text().startsWith(ListStyle.BULLET.marker(1).trim())
					&& !ListStyle.BULLET.marker(1).trim().isEmpty()) {
				strip(paragraph, ListStyle.BULLET.marker(1).trim().length(), ListStyle.BULLET);
			}
		}

		joinWrappedItems(paragraphs);

		int start = -1;
		int expected = 1;
		for (int i = 0; i <= paragraphs.size(); i++) {
			int marker = i < paragraphs.size() ? numberedMarker(paragraphs.get(i), expected) : -1;
			if (marker > 0) {
				if (start < 0) {
					start = i;
				}
				expected++;
				continue;
			}
			// A run has ended. One numbered line on its own is a sentence that happens to begin with
			// a figure far more often than it is a list of one.
			if (start >= 0 && i - start >= 2) {
				int ordinal = 1;
				for (int j = start; j < i; j++) {
					strip(paragraphs.get(j), numberedMarker(paragraphs.get(j), ordinal++), ListStyle.NUMBER);
				}
			}
			start = -1;
			expected = 1;
		}
	}

	/**
	 * Puts a list item that ran over two lines back together.
	 *
	 * <p>A written page has a line break at the end of every line, wrapped or not, so an item too
	 * long for one line comes back as an item and then a stray indented line. It looks right and
	 * behaves wrongly: pressing return at the end of that second line starts an ordinary paragraph,
	 * because that is what the second line now is.
	 *
	 * <p>Only where the first line was genuinely full. The next word not fitting on it is what makes
	 * a break a wrap rather than a return somebody pressed, and joining lines that were meant to be
	 * apart would be a worse fault than the one being mended.
	 */
	private static void joinWrappedItems(List<Paragraph> paragraphs) {
		for (int i = 0; i < paragraphs.size() - 1; i++) {
			Paragraph item = paragraphs.get(i);
			if (item.list() == ListStyle.NONE || item.isEmpty()) {
				continue;
			}
			float hang = Layout.hangingIndentOf(item.list().marker(1));
			while (i + 1 < paragraphs.size() && continues(item, paragraphs.get(i + 1), hang)) {
				Paragraph tail = paragraphs.remove(i + 1);
				item.insert(item.length(), " ", item.styleAt(item.length() - 1));
				item.append(tail);
			}
		}
	}

	/** Whether this paragraph is the rest of the item above it rather than a paragraph of its own. */
	private static boolean continues(Paragraph item, Paragraph next, float hang) {
		if (next.isEmpty() || next.list() != ListStyle.NONE
				|| next.alignment() != item.alignment() || next.indent() == 0) {
			return false;
		}
		// The indent it came back with has to be the room the marker took, not an indent somebody
		// asked for: a quotation set under a bullet is not part of the bullet.
		float indent = next.indent() * Layout.INDENT_SPACES * Widths.space();
		if (Math.abs(indent - hang) > Widths.space()) {
			return false;
		}
		String word = next.text().split("\\s+", 2)[0];
		float used = Widths.widthOf(item.text(), item.styleAt(0).bold());
		float wanted = used + Widths.advance(' ', false) + Widths.widthOf(word, next.styleAt(0).bold());
		return wanted > Layout.PAGE_WIDTH - hang;
	}

	/** How many characters the marker takes if this paragraph opens with exactly this number. */
	private static int numberedMarker(Paragraph paragraph, int ordinal) {
		String wanted = ListStyle.NUMBER.marker(ordinal).trim();
		return paragraph.text().startsWith(wanted) ? wanted.length() : -1;
	}

	/** Takes the marker and the blanks behind it off the front, and makes the paragraph a list item. */
	private static void strip(Paragraph paragraph, int markerLength, ListStyle style) {
		if (markerLength <= 0) {
			return;
		}
		int end = markerLength;
		while (end < paragraph.length() && paragraph.charAt(end) == ' ') {
			end++;
		}
		if (end >= paragraph.length()) {
			// Nothing but the marker: leave it alone rather than turn it into an empty list item.
			return;
		}
		// A list item is laid out differently from the line it came from: its gap after the marker
		// is ours, and its wrapped lines hang under the text. A page written without this mod – a
		// bullet typed by hand, a line the game wrapped back to the margin – would come back longer
		// than it was and no longer fit. Only take the item when nothing on the page moves.
		boolean bold = paragraph.styleAt(0).bold();
		float written = widthOf(paragraph, 0, end);
		float ours = Layout.hangingIndentOf(paragraph.text().substring(0, markerLength), bold);
		if (Math.abs(written - ours) > 0.5f) {
			return;
		}
		float indent = paragraph.indent() * Layout.INDENT_SPACES * Widths.space();
		if (indent + widthOf(paragraph, 0, paragraph.length()) > Layout.PAGE_WIDTH) {
			return;
		}
		paragraph.delete(0, end);
		paragraph.setList(style);
	}

	private static float widthOf(Paragraph paragraph, int from, int to) {
		float width = 0.0f;
		for (int i = from; i < to; i++) {
			width += Widths.advance(paragraph.charAt(i), paragraph.styleAt(i).bold());
		}
		return width;
	}

	/** Turns the characters gathered so far into a paragraph and empties the buffers. */
	private static Paragraph finishLine(StringBuilder text, List<QuillStyle> styles) {
		Paragraph paragraph = buildLine(text.toString(), styles);
		text.setLength(0);
		styles.clear();
		return paragraph;
	}

	private static Paragraph buildLine(String raw, List<QuillStyle> runStyles) {
		StringBuilder text = new StringBuilder(raw);
		List<QuillStyle> styles = new ArrayList<>(runStyles);

		// The leading blanks were an alignment before they were spaces.
		int lead = 0;
		while (lead < text.length() && text.charAt(lead) == ' ') {
			lead++;
		}
		float padWidth = 0.0f;
		for (int i = 0; i < lead; i++) {
			padWidth += Widths.advance(' ', styles.get(i).bold());
		}
		float bodyWidth = 0.0f;
		for (int i = lead; i < text.length(); i++) {
			bodyWidth += Widths.advance(text.charAt(i), styles.get(i).bold());
		}

		// What the blanks are depends on whether the line they open ever had to wrap.
		//
		// On a page this mod wrote, every line stands on its own and the blanks in front of it are
		// the alignment: reading them back as an alignment returns exactly what was meant. On a page
		// written in the vanilla editor, a line that runs past the margin is one paragraph that the
		// game breaks up as it draws it, and the blanks are a red line – they belong to the first
		// line of it and to no other. Taking those for an indent indented every line of the
		// paragraph, cost it eight pixels a line, and pushed the bottom of somebody else's page off
		// the bottom of it with "did not fit" underneath.
		//
		// So an indent is read back only where it can be meant: off a line that fits as it stands,
		// and only when the blanks come to a whole number of indent steps, which is what this mod
		// writes and what a person typing spaces by hand almost never lands on. Everything else stays
		// what it was – blanks, in the text, on the first line, exactly where the reader sees them.
		float slack = Layout.PAGE_WIDTH - bodyWidth;
		float step = Layout.INDENT_SPACES * Widths.space();
		// Where the text begins once the blanks in front of it have been accounted for. Nowhere at
		// all, unless they turn out to mean something: blanks that are not an alignment are text.
		int cut = 0;
		Alignment alignment = null;
		int indent = 0;
		if (lead > 0 && slack > 0.0f) {
			if (Math.abs(padWidth - slack / 2.0f) <= 2.5f) {
				alignment = Alignment.CENTER;
				cut = lead;
			} else if (Math.abs(padWidth - slack) <= 2.5f) {
				alignment = Alignment.RIGHT;
				cut = lead;
			} else if (step > 0.0f && Math.abs(padWidth - Math.round(padWidth / step) * step) <= 0.5f) {
				indent = Math.round(padWidth / step);
				cut = lead;
			}
		}

		Paragraph paragraph = new Paragraph();
		paragraph.insert(0, text.substring(cut), styles.subList(cut, styles.size()));
		if (alignment != null) {
			paragraph.setAlignment(alignment);
		}
		if (indent > 0) {
			paragraph.setIndent(indent);
		}
		return paragraph;
	}

	private static QuillStyle applyCode(QuillStyle style, char code) {
		for (int i = 0; i < COLOR_CODES.length; i++) {
			if (COLOR_CODES[i] == code) {
				if (i == 0) {
					// Black is read as no colour at all. On a page there is nothing to tell them
					// apart – black is the ink a book is printed in – and the only reason anyone
					// ever wrote §0 on one was to mean "and now nothing", this mod included, for as
					// long as it had no better way of saying so. Reading it as a colour somebody
					// chose made that old habit stick: the page came back black, it was written
					// black again, and it stayed unreadable on a torn page for ever.
					return QuillStyle.PLAIN;
				}
				Integer value = QuillStyle.LEGACY_COLORS[i].getColorValue();
				return QuillStyle.PLAIN.withColor(value == null ? QuillStyle.INHERIT : value);
			}
		}
		return switch (code) {
			case 'l' -> style.withBold(true);
			case 'm' -> style.withStrikethrough(true);
			case 'n' -> style.withUnderlined(true);
			case 'o' -> style.withItalic(true);
			case 'k' -> style.withObfuscated(true);
			case 'r' -> QuillStyle.PLAIN;
			default -> style;
		};
	}

	/**
	 * How often a page says "black", which is how it used to say "nothing at all".
	 *
	 * <p>The measure of whether a page is worth writing again. Black is invisible on parchment and
	 * unreadable on a torn page, so a page carrying any is a page to mend – but only if writing it
	 * again carries less, because a book offered for mending that cannot be mended would be offered
	 * every time it was opened for the rest of its life.
	 */
	public static int blackInk(String page) {
		int count = 0;
		for (int i = 0; i + 1 < page.length(); i++) {
			if (page.charAt(i) == SECTION) {
				if (page.charAt(i + 1) == '0') {
					count++;
				}
				i++;
			}
		}
		return count;
	}

	/** Strips every code, for counting words or searching. */
	public static String strip(String page) {
		StringBuilder out = new StringBuilder(page.length());
		for (int i = 0; i < page.length(); i++) {
			char c = page.charAt(i);
			if (c == SECTION && i + 1 < page.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}
}
