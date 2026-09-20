import com.glamardor.roleplayersquill.text.Alignment;
import com.glamardor.roleplayersquill.text.Columns;
import com.glamardor.roleplayersquill.text.FrameStyle;
import com.glamardor.roleplayersquill.text.Ornaments;
import com.glamardor.roleplayersquill.text.Hyphenator;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.ListStyle;
import com.glamardor.roleplayersquill.text.Paginator;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import com.glamardor.roleplayersquill.text.QuillStyle;
import com.glamardor.roleplayersquill.text.TableBuilder;
import com.glamardor.roleplayersquill.text.Widths;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the arithmetic this mod stands on, without a game.
 *
 * <p>Everything the editor claims – that a centred line is centred, that a justified one reaches
 * the right margin, that a page it says fits will fit – is a sum over glyph advances, and a sum
 * that is quietly two pixels out looks fine in a screenshot and wrong in a book. So the advances
 * are replaced with a font whose widths are known, the pages are laid out and written, and then the
 * written pages are measured again the way {@code TextHandler} will measure them: by walking the
 * string, resolving the codes, and adding up.
 *
 * <p>Measuring the output rather than the model is the point. A bug that affects both the layout
 * and the encoder identically would pass a test that only asked the layout what it thought.
 *
 * <p>Run with: java -cp "build/classes/java/main;<minecraft>;<slf4j>" tools/LayoutCheck.java
 */
public final class LayoutCheck {
	private static final float PAGE = Layout.PAGE_WIDTH;
	private static int failures;
	private static int checks;

	public static void main(String[] args) {
		Widths.useSource(LayoutCheck::advance);

		checkPadding();
		checkAlignment();
		checkJustification();
		checkNoRewrap();
		checkRoundTrip();
		checkHyphenation();
		checkPagination();
		checkTables();
		checkRules();
		checkStrayCode();
		checkBlankRemainder();
		checkReaderAgrees();
		checkPlainStaysPlain();
		checkPageBudget();

		System.out.println();
		System.out.println(checks + " checks, " + failures + " failed");
		if (failures > 0) {
			System.exit(1);
		}
	}

	// ---- the stand-in font -------------------------------------------------------------------------

	/**
	 * Close enough to the real thing to be worth measuring against: four pixels for a space, five in
	 * bold, and the narrow letters narrow.
	 */
	private static float advance(int codePoint, boolean bold) {
		int base;
		if (codePoint == ' ') {
			base = 4;
		} else if (codePoint == '•') {
			// Narrow on purpose: the real bullet is three pixels, and three plus a space is seven –
			// one of the handful of widths a run of spaces cannot be. A wider stand-in would make
			// the list checks below pass for a reason the game does not share.
			base = 3;
		} else if ("il.,:;!|'".indexOf(codePoint) >= 0) {
			base = 2;
		} else if ("fkt()[]\"*<>{}".indexOf(codePoint) >= 0) {
			base = 5;
		} else {
			base = 6;
		}
		return base + (bold ? 1 : 0);
	}

	/** The width of a written line, read the way the game reads it: codes resolved as it goes. */
	private static float measure(String line) {
		float total = 0.0f;
		boolean bold = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == LegacyCodec.SECTION && i + 1 < line.length()) {
				char code = Character.toLowerCase(line.charAt(i + 1));
				if (code == 'l') {
					bold = true;
				} else if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) {
					bold = false;
				}
				i++;
				continue;
			}
			total += advance(c, bold);
		}
		return total;
	}

	/** The visible text of a line, with the codes taken out. */
	private static String plain(String line) {
		return LegacyCodec.strip(line);
	}

	// ---- the checks ---------------------------------------------------------------------------------

	private static void checkPadding() {
		section("Padding lands on the pixel");
		// Below four pixels there is nothing to pad with: the narrowest thing that can be put in
		// front of a line without being seen is a space, and a space is four pixels. So a line that
		// needs one, two or three pixels of indent gets none, and the error is the indent itself.
		float worst = 0.0f;
		for (int target = 4; target <= 200; target++) {
			Widths.Padding padding = Widths.pad(target);
			expect(padding.width() <= target + 0.001f,
					"pad(" + target + ") overshot: " + padding.width());
			worst = Math.max(worst, target - padding.width());
			// What the padding will actually be worth once written out.
			float written = padding.count() * 4 + padding.bold();
			expect(Math.abs(written - padding.width()) < 0.001f,
					"pad(" + target + ") promises " + padding.width() + " but writes " + written);
		}
		expect(worst <= 2.0f, "the worst padding error above four pixels is " + worst);
		for (int target = 0; target < 4; target++) {
			expect(Widths.pad(target).isEmpty(), "pad(" + target + ") produced something");
		}
		System.out.println("  4 px and up: at most " + worst + " px short. Under 4 px: nothing to pad with.");
	}

	private static void checkAlignment() {
		section("Centred and right-aligned lines sit where they should");
		for (Alignment alignment : new Alignment[] { Alignment.CENTER, Alignment.RIGHT }) {
			for (String sample : samples()) {
				Paragraph paragraph = new Paragraph(sample, QuillStyle.PLAIN);
				paragraph.setAlignment(alignment);
				List<Paragraph> page = List.of(paragraph);
				List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
				String written = LegacyCodec.encode(page, lines);

				for (String line : written.split("\n", -1)) {
					String body = plain(line);
					int lead = 0;
					while (lead < body.length() && body.charAt(lead) == ' ') {
						lead++;
					}
					float padWidth = measure(leadingOf(line));
					float textWidth = measure(line) - padWidth;
					float wanted = alignment == Alignment.CENTER ? (PAGE - textWidth) / 2.0f : PAGE - textWidth;
					expect(Math.abs(padWidth - wanted) <= 2.0f,
							alignment + " off by " + Math.abs(padWidth - wanted) + " px on: " + body);
					expect(measure(line) <= PAGE + 0.001f,
							alignment + " produced a line " + measure(line) + " px wide");
				}
			}
		}
	}

	/** The run of blanks at the front of a written line, codes and all. */
	private static String leadingOf(String line) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == LegacyCodec.SECTION && i + 1 < line.length()) {
				out.append(c).append(line.charAt(i + 1));
				i++;
				continue;
			}
			if (c != ' ') {
				break;
			}
			out.append(c);
		}
		return out.toString();
	}

	private static void checkJustification() {
		section("Justified lines reach the right margin");
		Paragraph paragraph = new Paragraph(longRussian(), QuillStyle.PLAIN);
		paragraph.setAlignment(Alignment.JUSTIFY);
		List<Paragraph> page = List.of(paragraph);
		List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
		String written = LegacyCodec.encode(page, lines);
		String[] out = written.split("\n", -1);

		expect(out.length == lines.size(), "wrote " + out.length + " lines for " + lines.size() + " laid out");
		for (int i = 0; i < out.length; i++) {
			float width = measure(out[i]);
			expect(width <= PAGE + 0.001f, "justified line " + i + " is " + width + " px wide");
			boolean last = i == out.length - 1;
			if (!last) {
				expect(width >= PAGE - 1.0f,
						"justified line " + i + " stopped " + (PAGE - width) + " px short: " + plain(out[i]));
			}
		}
		System.out.println("  " + out.length + " lines, all of them flush to 114 px but the last");
	}

	/**
	 * The written page must not wrap again when the game lays it out.
	 *
	 * <p>Every line this mod writes ends in a real line break, so the reader should find exactly the
	 * lines that were written. If one of them is a pixel too wide the game breaks it in two, the
	 * page grows a line it was not supposed to have, and the last line falls off the bottom.
	 */
	private static void checkNoRewrap() {
		section("The game will not re-wrap what was written");
		List<Paragraph> page = new ArrayList<>();
		for (Alignment alignment : Alignment.values()) {
			Paragraph paragraph = new Paragraph(longRussian(), QuillStyle.PLAIN);
			paragraph.setAlignment(alignment);
			page.add(paragraph);
		}
		Paragraph listed = new Paragraph("Список из одного длинного пункта, который точно не влезет в строку",
				QuillStyle.PLAIN);
		listed.setList(ListStyle.NUMBER);
		page.add(listed);

		List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
		String written = LegacyCodec.encode(page, lines);
		int wrapped = wrapLikeTheGame(written);
		expect(wrapped == lines.size(),
				"laid out " + lines.size() + " lines, the game would show " + wrapped);
		System.out.println("  " + lines.size() + " lines laid out, " + wrapped + " after the game wraps them");

		section("A list item that wraps keeps its left edge straight");
		for (ListStyle style : ListStyle.values()) {
			if (style == ListStyle.NONE) {
				continue;
			}
			List<Paragraph> items = new ArrayList<>();
			Paragraph item = new Paragraph(longRussian(), QuillStyle.PLAIN);
			item.setList(style);
			items.add(item);
			List<Layout.LaidLine> laid = Layout.lay(items, Layout.Options.DEFAULT);
			expect(laid.size() > 1, "the list item did not wrap, so there is nothing to line up");
			checkTextStartsTogether(style, LegacyCodec.encode(items, laid));
		}

		section("A list is still a list after the book has been written and opened again");
		List<Paragraph> items = new ArrayList<>();
		for (String line : new String[] {"Первый пункт", "Второй пункт", "Третий пункт"}) {
			Paragraph bullet = new Paragraph(line, QuillStyle.PLAIN);
			bullet.setList(ListStyle.BULLET);
			items.add(bullet);
		}
		for (String line : new String[] {"Раз", "Два", "Три"}) {
			Paragraph number = new Paragraph(line, QuillStyle.PLAIN);
			number.setList(ListStyle.NUMBER);
			items.add(number);
		}
		String writtenList = LegacyCodec.encode(items, Layout.lay(items, Layout.Options.DEFAULT));
		List<Paragraph> reopened = LegacyCodec.decode(writtenList);
		expect(reopened.size() == items.size(),
				"the list came back as " + reopened.size() + " paragraphs instead of " + items.size());
		for (int i = 0; i < Math.min(reopened.size(), items.size()); i++) {
			expect(reopened.get(i).list() == items.get(i).list(),
					"paragraph " + i + " came back as " + reopened.get(i).list()
							+ " instead of " + items.get(i).list());
			expect(reopened.get(i).text().equals(items.get(i).text()),
					"paragraph " + i + " came back as \"" + reopened.get(i).text() + "\"");
		}
		// And writing it out again has to give the same page, or reopening a book would slowly
		// change it: a marker read as a marker and then written back as one plus its old text.
		expect(LegacyCodec.encode(reopened, Layout.lay(reopened, Layout.Options.DEFAULT)).equals(writtenList),
				"writing the reopened list back gave a different page");

		section("Two columns line up, whatever the left one ends on");
		// The guarantee the column widths were chosen for: a left line can be any width up to the
		// column's, and the run of spaces after it must reach the second column exactly every time.
		for (int used = 0; used <= (int) Columns.COLUMN_WIDTH; used++) {
			float gap = Columns.SECOND_COLUMN - used;
			expect(Math.abs(Widths.pad(gap).width() - gap) < 0.001f,
					"a left line " + used + " px wide cannot reach the second column: " + gap + " px");
		}
		List<Paragraph> wide = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			wide.add(new Paragraph(longRussian(), QuillStyle.PLAIN));
		}
		for (List<Paragraph> built : Columns.split(wide, Layout.Options.DEFAULT)) {
			String inColumns = LegacyCodec.encode(built, Layout.lay(built, Layout.Options.DEFAULT));
			for (String line : inColumns.split("\n", -1)) {
				expect(measure(line) <= PAGE + 0.001f,
						"a two-column row is " + measure(line) + " px wide");
			}
			expect(Layout.lay(built, Layout.Options.DEFAULT).size() == built.size(),
					"a two-column row wrapped instead of fitting");
		}

		section("A frame keeps both its bars in the same place on every line");
		for (FrameStyle style : FrameStyle.values()) {
			if (!style.present()) {
				continue;
			}
			List<Paragraph> inside = new ArrayList<>();
			inside.add(new Paragraph(longRussian(), QuillStyle.PLAIN));
			Paragraph centred = new Paragraph("Указ", QuillStyle.PLAIN);
			centred.setAlignment(Alignment.CENTER);
			inside.add(centred);
			for (String word : new String[] {"а", "два", "четыре", "шестьшесть"}) {
				inside.add(new Paragraph(word, QuillStyle.PLAIN));
			}
			List<Paragraph> framed = Ornaments.frame(inside, style);
			String bordered = LegacyCodec.encode(framed, Layout.lay(framed, Layout.Options.DEFAULT));
			for (String line : bordered.split("\n", -1)) {
				expect(measure(line) <= PAGE + 0.001f,
						style + " made a line " + measure(line) + " px wide");
				String plain = plain(line);
				if (plain.isEmpty() || plain.charAt(0) != style.bar) {
					// The top and bottom edges are a run of rule characters, not a bar and a bar.
					continue;
				}
				expect(plain.charAt(plain.length() - 1) == style.bar,
						style + " lost the bar off the end of a line");
				float upToBar = measure(line.substring(0, line.lastIndexOf(style.bar)));
				expect(Math.abs(upToBar - style.barRight()) < 0.001f,
						style + " put the right-hand bar at " + upToBar + " instead of " + style.barRight());
			}
		}

		section("A leader fills the line exactly and leaves no tab behind");
		for (String tail : new String[] {"30 монет", "1", "очень длинная цена в монетах"}) {
			List<Paragraph> priced = new ArrayList<>();
			priced.add(new Paragraph("Меч" + Widths.LEADER + tail, QuillStyle.PLAIN));
			List<Layout.LaidLine> laid = Layout.lay(priced, Layout.Options.DEFAULT);
			String line = LegacyCodec.encode(priced, laid);
			expect(line.indexOf(Widths.LEADER) < 0, "a tab was written to the page");
			if (laid.size() == 1) {
				expect(Math.abs(measure(line) - PAGE) < 0.001f,
						"a leader line came to " + measure(line) + " px instead of " + PAGE);
			}
		}

		// A real page from a real book: bullets, and items long enough to wrap. A wrapped item comes
		// back as two lines on the page, and has to be one item again by the time it is a document.
		String[] punishments = {"публичная порка", "казнь шпицрутенами", "публичное закидывание КО",
				"окунание в яму с гнилью", "отработка в поле"};
		List<Paragraph> real = new ArrayList<>();
		for (String line : punishments) {
			Paragraph bullet = new Paragraph(line, QuillStyle.PLAIN);
			bullet.setList(ListStyle.BULLET);
			real.add(bullet);
		}
		List<Paragraph> reread = LegacyCodec.decode(
				LegacyCodec.encode(real, Layout.lay(real, Layout.Options.DEFAULT)));
		expect(reread.size() == punishments.length,
				"a page of bullets came back as " + reread.size() + " paragraphs instead of " + punishments.length);
		for (int i = 0; i < Math.min(reread.size(), punishments.length); i++) {
			expect(reread.get(i).list() == ListStyle.BULLET,
					"bullet " + i + " came back as " + reread.get(i).list());
			expect(reread.get(i).text().equals(punishments[i]),
					"bullet " + i + " came back as \"" + reread.get(i).text() + "\"");
		}

		section("A book written without this mod is not rearranged by it");

		// A page as the vanilla editor leaves one: no line breaks at all, because the game wraps the
		// text as it draws it, and a red line typed as blanks in front of the first word. Reading
		// those blanks as an indent cost every line of the paragraph eight pixels, which pushed the
		// bottom of somebody else's page off the bottom of it.
		String body = "Мы, милостью богов, объявляем всем подданным нашего королевства, что "
				+ "означенный день будет отмечен ярмаркой, и всякий, кто пожелает торговать, "
				+ "да придёт к ратуше до полудня, ибо после полудня места разобраны будут.";
		String vanilla = "    " + body;
		List<Paragraph> asWritten = LegacyCodec.decode(vanilla);
		expect(asWritten.size() == 1,
				"a page with no line breaks came back as " + asWritten.size() + " paragraphs");
		expect(asWritten.get(0).indent() == 0,
				"a red line typed by hand came back as an indent of " + asWritten.get(0).indent());
		expect(asWritten.get(0).alignment() == Alignment.LEFT,
				"a red line typed by hand came back aligned " + asWritten.get(0).alignment());
		expect(asWritten.get(0).text().equals(vanilla),
				"the blanks in front of a paragraph were taken out of the text");

		int byTheGame = greedyLines(vanilla);
		int laidOut = Layout.lay(asWritten, Layout.Options.DEFAULT).size();
		expect(laidOut <= byTheGame,
				"the game wraps this page into " + byTheGame + " lines and this mod lays it into " + laidOut);

		// An indent this mod wrote is still read back as an indent: it lands on a whole step, which
		// is the difference between a measurement and a guess.
		List<Paragraph> stepped = LegacyCodec.decode("        Отступ.");
		expect(stepped.get(0).indent() == 4,
				"an indent this mod wrote came back as " + stepped.get(0).indent());

		// And a page nobody edited goes back out as the very string it came in as.
		QuillDocument received = new QuillDocument();
		String borrowed = "  Страница из чужой книги, с её собственными пробелами.";
		received.rememberSource(LegacyCodec.decode(borrowed), borrowed);
		expect(borrowed.equals(received.sourceOf(LegacyCodec.decode(borrowed))),
				"a page nobody touched was not recognised as the page that arrived");
		List<Paragraph> touched = LegacyCodec.decode(borrowed);
		touched.get(0).insert(0, "!", QuillStyle.PLAIN);
		expect(received.sourceOf(touched) == null,
				"an edited page still passed for the page that arrived");

		// The vanilla editor is a text box that counts lines, not a page that draws them, and it has
		// room for exactly fourteen. An empty line hanging off the end of a page is invisible to a
		// reader and is a fifteenth line to that box: it grows a scrollbar and slides the text under
		// itself for everybody without this mod.
		List<Paragraph> trailing = new ArrayList<>();
		trailing.add(new Paragraph("Одна строчка.", QuillStyle.PLAIN));
		trailing.add(new Paragraph());
		trailing.add(new Paragraph());
		String tail = LegacyCodec.encode(trailing, Layout.lay(trailing, Layout.Options.DEFAULT));
		expect(!tail.endsWith("\n"), "a page was written ending in a line break");
		expect(tail.split("\n", -1).length == 1,
				"a page with two empty lines after it was written as "
						+ tail.split("\n", -1).length + " lines");

		// The same page counted the way the vanilla editor counts it.
		expect(LegacyCodec.editorLines("одна\nдве\nтри") == 3,
				"three lines were counted as " + LegacyCodec.editorLines("одна\nдве\nтри"));
		expect(LegacyCodec.editorLines("одна\n") == 2,
				"a page ending in a break was counted as " + LegacyCodec.editorLines("одна\n"));
		expect(LegacyCodec.fitsTheVanillaEditor("одна\nдве"),
				"a two-line page was called too big for the vanilla editor");
		expect(!LegacyCodec.fitsTheVanillaEditor("1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n13\n14\n"),
				"fourteen lines and an empty one after them passed for a page that fits");
		expect(LegacyCodec.fitsTheVanillaEditor("1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n13\n14"),
				"exactly fourteen lines were called too many");

		// A blank line between two paragraphs is not trailing and is still written.
		List<Paragraph> spaced = new ArrayList<>();
		spaced.add(new Paragraph("Первая.", QuillStyle.PLAIN));
		spaced.add(new Paragraph());
		spaced.add(new Paragraph("Вторая.", QuillStyle.PLAIN));
		expect(LegacyCodec.encode(spaced, Layout.lay(spaced, Layout.Options.DEFAULT))
						.split("\n", -1).length == 3,
				"a blank line between two paragraphs was lost");

		// The name a signature is written with comes off the tab list, where a server hangs a rank,
		// an id and a colour on it. None of that is the character's name.
		expect(com.glamardor.roleplayersquill.text.TextSet.clean("§a[Лорд] §fЭлиандрэль")
						.equals("Элиандрэль"),
				"a rank in brackets was kept in the signature: \""
						+ com.glamardor.roleplayersquill.text.TextSet.clean("§a[Лорд] §fЭлиандрэль") + "\"");
		expect(com.glamardor.roleplayersquill.text.TextSet.clean("[123][RP] Грэй Лунгарр")
						.equals("Грэй Лунгарр"),
				"two bracketed pieces were not both taken off the name");
		expect(com.glamardor.roleplayersquill.text.TextSet.clean("Флири Анемониа")
						.equals("Флири Анемониа"),
				"a plain name was changed");

		section("Things that only look like lists are left alone");
		List<Paragraph> speech = new ArrayList<>();
		speech.add(new Paragraph("– Здравствуйте, – сказал он.", QuillStyle.PLAIN));
		speech.add(new Paragraph("– И вам не хворать.", QuillStyle.PLAIN));
		speech.add(new Paragraph("5. Пятый пункт какого-то другого списка", QuillStyle.PLAIN));
		List<Paragraph> asRead = LegacyCodec.decode(
				LegacyCodec.encode(speech, Layout.lay(speech, Layout.Options.DEFAULT)));
		for (Paragraph paragraph : asRead) {
			expect(paragraph.list() == ListStyle.NONE,
					"\"" + paragraph.text() + "\" was read as a " + paragraph.list() + " list");
		}

		section("The same, with hyphenation on");
		Layout.Options hyphenated = new Layout.Options(true, false, 2, 2);
		List<Paragraph> dense = new ArrayList<>();
		for (Alignment alignment : Alignment.values()) {
			Paragraph paragraph = new Paragraph(longRussian(), QuillStyle.PLAIN);
			paragraph.setAlignment(alignment);
			dense.add(paragraph);
		}
		List<Layout.LaidLine> denseLines = Layout.lay(dense, hyphenated);
		String denseWritten = LegacyCodec.encode(dense, denseLines);
		expect(wrapLikeTheGame(denseWritten) == denseLines.size(),
				"hyphenated text wrapped again: " + wrapLikeTheGame(denseWritten) + " of " + denseLines.size());

		int broken = 0;
		float used = 0.0f;
		for (Layout.LaidLine line : denseLines) {
			if (line.hyphen) {
				broken++;
			}
		}
		for (String line : denseWritten.split("\n", -1)) {
			used += measure(line);
		}
		expect(broken > 0, "hyphenation was on and nothing was hyphenated");
		float plainFill = 0.0f;
		for (String line : LegacyCodec.encode(dense, Layout.lay(dense, Layout.Options.DEFAULT)).split("\n", -1)) {
			plainFill += measure(line);
		}
		System.out.println("  " + broken + " words broken, " + denseLines.size()
				+ " lines instead of " + Layout.lay(dense, Layout.Options.DEFAULT).size()
				+ ", average line " + Math.round(used / denseLines.size())
				+ " px against " + Math.round(plainFill / Layout.lay(dense, Layout.Options.DEFAULT).size()));
	}

	/**
	 * The same rule {@code TextHandler.LineBreakingVisitor} uses: keep taking characters while the
	 * total is not over the width, break at the last space when it is, and treat a line break as a
	 * break that costs one character.
	 */
	/**
	 * How many lines the game draws a written page as.
	 *
	 * <p>Transcribed by hand from {@code TextHandler.LineBreakingVisitor} and kept apart from the
	 * mod's own copy of the same rule on purpose: a check that calls the code it is checking proves
	 * only that the code agrees with itself. A blank is noted as a place the line may end before the
	 * width is tested; the line that goes over ends at the last blank noted, and what followed that
	 * blank begins the next line.
	 */
	private static int wrapLikeTheGame(String page) {
		int lines = 1;
		boolean bold = false;
		float total = 0.0f;
		float beforeSpace = 0.0f;
		float spaceWidth = 0.0f;
		boolean haveSpace = false;
		boolean anything = false;

		for (int i = 0; i < page.length(); i++) {
			char c = page.charAt(i);
			if (c == LegacyCodec.SECTION && i + 1 < page.length()) {
				char code = Character.toLowerCase(page.charAt(i + 1));
				if (code == 'l') {
					bold = true;
				} else if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) {
					bold = false;
				}
				i++;
				continue;
			}
			if (c == '\n') {
				lines++;
				total = 0.0f;
				haveSpace = false;
				anything = false;
				continue;
			}
			float advance = advance(c, bold);
			if (c == ' ') {
				haveSpace = true;
				beforeSpace = total;
				spaceWidth = advance;
			}
			total += advance;
			if (anything && total > PAGE) {
				lines++;
				total = haveSpace ? total - beforeSpace - spaceWidth : advance;
				haveSpace = false;
				anything = total != 0.0f;
				continue;
			}
			anything |= advance != 0.0f;
		}
		return lines;
	}

	private static void checkRoundTrip() {
		section("A page read back and written again is the same page");
		Paragraph paragraph = new Paragraph("Обычный, ", QuillStyle.PLAIN);
		paragraph.insert(paragraph.length(), "жирный", QuillStyle.PLAIN.withBold(true));
		paragraph.insert(paragraph.length(), ", ", QuillStyle.PLAIN);
		paragraph.insert(paragraph.length(), "красный", QuillStyle.PLAIN.withColor(0xFF5555));
		paragraph.insert(paragraph.length(), " и ", QuillStyle.PLAIN);
		paragraph.insert(paragraph.length(), "подчёркнутый", QuillStyle.PLAIN.withUnderlined(true));

		List<Paragraph> page = List.of(paragraph);
		String once = LegacyCodec.encode(page, Layout.lay(page, Layout.Options.DEFAULT));
		List<Paragraph> read = LegacyCodec.decode(once);
		String twice = LegacyCodec.encode(read, Layout.lay(read, Layout.Options.DEFAULT));
		expect(once.equals(twice), "round trip changed the page:\n    " + once + "\n    " + twice);
		System.out.println("  " + once.replace("§", "&"));
	}

	private static void checkHyphenation() {
		section("Russian words break where a Russian reader expects");
		expectPoints("молоко", "мо|ло|ко");
		expectPoints("сестра", "сес|тра");
		expectPoints("подъезд", "подъ|езд");
		expectPoints("майка", "май|ка");
		expectPoints("стол", "");
		expectPoints("объявление", "объ|яв|ле|ние");

		section("English words break somewhere sensible");
		expectPoints("hyphenation", "hy|phen|ation");
		expectPoints("running", "run|ning");
	}

	private static void expectPoints(String word, String expected) {
		List<Integer> points = Hyphenator.points(word, 2, 2);
		StringBuilder shown = new StringBuilder();
		int previous = 0;
		for (int point : points) {
			shown.append(word, previous, point).append('|');
			previous = point;
		}
		shown.append(word.substring(previous));
		String got = points.isEmpty() ? "" : shown.toString();
		boolean ok = expected.isEmpty() ? points.isEmpty() : got.equals(expected);
		expect(ok, word + " broke as " + (got.isEmpty() ? "(not at all)" : got) + ", expected "
				+ (expected.isEmpty() ? "(not at all)" : expected));
		if (ok) {
			System.out.println("  " + (got.isEmpty() ? word + " (whole)" : got));
		}
	}

	private static void checkPagination() {
		section("A long text fills whole pages and loses nothing");
		StringBuilder source = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			source.append(longRussian()).append('\n');
		}
		List<Paragraph> flow = Paginator.parse(source.toString(), false, QuillStyle.PLAIN, Alignment.JUSTIFY);
		Paginator.Options options = Paginator.Options.of(Layout.Options.DEFAULT, false);
		List<List<Paragraph>> pages = Paginator.paginate(flow, options);

		StringBuilder before = new StringBuilder();
		for (Paragraph paragraph : flow) {
			before.append(paragraph.text());
		}
		StringBuilder after = new StringBuilder();
		for (List<Paragraph> page : pages) {
			List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
			int written = LegacyCodec.encode(page, lines).length();
			expect(lines.size() <= Layout.PAGE_LINES, "a page came out with " + lines.size() + " lines");
			expect(written <= QuillDocument.MAX_PAGE_CHARS,
					"a page came out at " + written + " characters");
			for (Paragraph paragraph : page) {
				after.append(paragraph.text());
			}
		}
		// The words survive; the spaces at the breaks do not, and should not.
		expect(squash(after.toString()).equals(squash(before.toString())),
				"pagination lost or gained text");
		System.out.println("  " + pages.size() + " pages, every one inside both limits, no text lost");
	}

	private static String squash(String text) {
		return text.replaceAll("\\s+", " ").strip();
	}

	private static void checkTables() {
		section("Table columns line up and fit");
		List<List<String>> rows = TableBuilder.parse("Товар|Цена\nХлеб|3\nЯблоко|1\nСоль|12");
		expect(TableBuilder.fits(rows, TableBuilder.Style.RULES, TableBuilder.Heading.BOLD), "a two-column table did not fit");
		List<Paragraph> table = TableBuilder.build(rows, TableBuilder.Style.RULES, TableBuilder.Columns.AUTO, TableBuilder.Heading.BOLD);
		List<Layout.LaidLine> lines = Layout.lay(table, Layout.Options.DEFAULT);
		expect(lines.size() == table.size(), "a table row wrapped: " + lines.size() + " lines for " + table.size() + " rows");

		String written = LegacyCodec.encode(table, lines);
		for (String line : written.split("\n", -1)) {
			expect(measure(line) <= PAGE + 0.001f, "a table row is " + measure(line) + " px wide");
		}
		for (String line : written.split("\n", -1)) {
			System.out.println(String.format("  %5.1f px |%s|", measure(line),
					line.replace(LegacyCodec.SECTION, '&')));
		}

		// The same with a rule and bars, which is the shape that came out crooked.
		List<List<String>> grid = TableBuilder.parse("Товар|Цена|Кол\nХлеб|3|3\nЯблоко|1|3");
		List<Paragraph> barred = TableBuilder.build(grid, TableBuilder.Style.GRID, TableBuilder.Columns.AUTO, TableBuilder.Heading.BOLD);
		List<Layout.LaidLine> barredLines = Layout.lay(barred, Layout.Options.DEFAULT);
		String barredWritten = LegacyCodec.encode(barred, barredLines);
		System.out.println("  -- with a rule and bars --");
		for (String line : barredWritten.split("\n", -1)) {
			System.out.println(String.format("  %5.1f px |%s|", measure(line),
					line.replace(LegacyCodec.SECTION, '&')));
			expect(measure(line) <= PAGE + 0.001f, "a barred row is " + measure(line) + " px wide");
		}
		// Every bar has to stand in the same place on every row, or the table reads as broken.
		checkBarsLineUp(barredWritten);

		// A shape where the obvious widths are all blocked. The cells differ by one, two and three
		// pixels, so with the column at its natural width – and at one, two, three, four and five
		// pixels wider – some row always ends up with a gap of six, seven or eleven, which a run of
		// spaces cannot be. Six pixels wider is the first width that suits every row at once, and a
		// search that gives up before then leaves the bars in a staircase. This is that table.
		List<List<String>> awkward = TableBuilder.parse("a|N\naaa|1\naff|2\nfff|3");
		List<Paragraph> steps = TableBuilder.build(awkward, TableBuilder.Style.GRID,
				TableBuilder.Columns.AUTO, TableBuilder.Heading.BOLD);
		String stepsWritten = LegacyCodec.encode(steps, Layout.lay(steps, Layout.Options.DEFAULT));
		System.out.println("  -- the awkward widths --");
		for (String line : stepsWritten.split("\n", -1)) {
			System.out.println(String.format("  %5.1f px |%s|", measure(line),
					line.replace(LegacyCodec.SECTION, '&')));
		}
		checkBarsLineUp(stepsWritten);
	}

	/**
	 * Where the words begin on each line of a list item, which has to be the same place every time.
	 *
	 * <p>Measured on the written page rather than on the layout, because the layout is the thing
	 * being checked: it can believe the second line starts under the first while the spaces it wrote
	 * say otherwise. The words begin after the last blank of the run that opens the line, and on the
	 * first line after the marker as well.
	 */
	private static void checkTextStartsTogether(ListStyle style, String written) {
		Float first = null;
		for (String line : written.split("\n", -1)) {
			float x = 0.0f;
			boolean bold = false;
			boolean counting = true;
			Float start = null;
			for (int i = 0; i < line.length() && start == null; i++) {
				char c = line.charAt(i);
				if (c == LegacyCodec.SECTION && i + 1 < line.length()) {
					char code = Character.toLowerCase(line.charAt(i + 1));
					if (code == 'l') {
						bold = true;
					} else if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) {
						bold = false;
					}
					i++;
					continue;
				}
				if (counting && (c == ' ' || style.marker(1).indexOf(c) >= 0)) {
					x += advance(c, bold);
					continue;
				}
				counting = false;
				start = x;
			}
			if (start == null) {
				continue;
			}
			if (first == null) {
				first = start;
			} else {
				expect(Math.abs(first - start) < 0.001f, "in a " + style + " list the text starts at "
						+ first + " on the first line and at " + start + " on a later one");
			}
		}
	}

	/** The x of every bar on every line, which must be the same set of numbers for each row. */
	private static void checkBarsLineUp(String written) {
		List<float[]> rows = new ArrayList<>();
		for (String line : written.split("\n", -1)) {
			List<Float> found = new ArrayList<>();
			float x = 0.0f;
			boolean bold = false;
			for (int i = 0; i < line.length(); i++) {
				char c = line.charAt(i);
				if (c == LegacyCodec.SECTION && i + 1 < line.length()) {
					char code = Character.toLowerCase(line.charAt(i + 1));
					if (code == 'l') {
						bold = true;
					} else if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) {
						bold = false;
					}
					i++;
					continue;
				}
				if (c == '│') {
					found.add(x);
				}
				x += advance(c, bold);
			}
			if (!found.isEmpty()) {
				float[] array = new float[found.size()];
				for (int i = 0; i < array.length; i++) {
					array[i] = found.get(i);
				}
				rows.add(array);
			}
		}
		if (rows.size() < 2) {
			return;
		}
		float[] first = rows.get(0);
		for (int r = 1; r < rows.size(); r++) {
			float[] row = rows.get(r);
			expect(row.length == first.length,
					"row " + r + " has " + row.length + " bars where the first has " + first.length);
			if (row.length != first.length) {
				continue;
			}
			for (int i = 0; i < row.length; i++) {
				expect(Math.abs(row[i] - first[i]) < 0.5f,
						"bar " + i + " stands at " + first[i] + " on the first row and " + row[i]
								+ " on row " + r);
			}
		}
	}

	/**
	 * A rule is nothing but underlined blanks, and blanks at the end of a line are exactly what the
	 * layout throws away to stop trailing spaces pushing the alignment about. It threw the rule away
	 * with them.
	 */
	private static void checkRules() {
		section("A horizontal rule is not mistaken for trailing space");
		Paragraph rule = TableBuilder.rule();
		List<Paragraph> page = List.of(rule);
		List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
		expect(lines.size() == 1, "the rule came out as " + lines.size() + " lines");
		expect(lines.get(0).contentEnd > 0, "the rule was trimmed away to nothing");

		String written = LegacyCodec.encode(page, lines);
		expect(written.contains(LegacyCodec.SECTION + "n"), "the rule was written without an underline");
		float width = measure(written);
		expect(width > PAGE - 3 && width <= PAGE, "the rule is " + width + " px instead of " + PAGE);
		System.out.println("  " + width + " px of underline, " + written.length() + " characters");

		// And the trimming still happens where it should.
		Paragraph padded = new Paragraph("слово   ", QuillStyle.PLAIN);
		List<Paragraph> plainPage = List.of(padded);
		String plainWritten = LegacyCodec.encode(plainPage, Layout.lay(plainPage, Layout.Options.DEFAULT));
		expect(!plainWritten.endsWith(" "), "ordinary trailing blanks were kept: '" + plainWritten + "'");
		System.out.println("  ordinary trailing blanks still dropped");
	}

	/**
	 * Reading a page has to agree with the game character for character.
	 *
	 * <p>A page written by hand or by another mod can end a line with a stray section sign, and the
	 * game's own reader swallows the character after one whether it is a code or not – so the line
	 * break goes with it and the two lines are really one. Reading the page as lines first and the
	 * codes afterwards produced a page with a line too many, which then claimed not to fit.
	 */
	private static void checkStrayCode() {
		section("A page is read exactly as the game reads it");

		List<Paragraph> eaten = LegacyCodec.decode("Склад - Скельд Холодный" + LegacyCodec.SECTION + "\nАрхитектура");
		expect(eaten.size() == 1, "a swallowed line break still split the page into " + eaten.size());
		expect(eaten.get(0).text().equals("Склад - Скельд ХолодныйАрхитектура"),
				"came out as " + eaten.get(0).text());

		List<Paragraph> trailing = LegacyCodec.decode("конец" + LegacyCodec.SECTION);
		expect(trailing.get(0).text().equals("конец"), "a section sign at the very end was kept as text");

		List<Paragraph> carried = LegacyCodec.decode(LegacyCodec.SECTION + "cпервая\nвторая");
		expect(carried.size() == 2, "a real line break was lost");
		expect(carried.get(1).length() > 0 && carried.get(1).styleAt(0).color() != QuillStyle.INHERIT,
				"the colour did not carry over the line break, but it does for a reader");
		System.out.println("  swallowed breaks, trailing signs and carried colour all agree with the reader");
	}

	/**
	 * A line whose remainder is nothing but blanks must not become an empty line of its own – the
	 * blanks are about to be trimmed, and the reader never had a line there at all.
	 */
	private static void checkBlankRemainder() {
		section("Trailing blanks do not become a line of their own");
		// Nineteen letters at six pixels is exactly the width of the page; the space after them
		// cannot fit, so the layout breaks there and finds nothing but a blank left over.
		Paragraph paragraph = new Paragraph("а".repeat(19) + " ", QuillStyle.PLAIN);
		List<Paragraph> page = List.of(paragraph);
		List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
		expect(lines.size() == 1, "came out as " + lines.size() + " lines instead of one");
		System.out.println("  a full line plus a trailing blank is one line, not two");
	}

	/**
	 * What the editor shows and what a reader sees must be the same thing.
	 *
	 * <p>The editor draws the document; a reader resolves the codes the encoder wrote. So the two
	 * agree exactly when every visible character comes out of the codes wearing the style the
	 * document gave it. That is checked here character by character, on a page built to put every
	 * switch and every colour next to every other one – which is the only way to catch a transition
	 * the encoder gets wrong in one direction and right in the other.
	 */
	private static void checkReaderAgrees() {
		section("A reader sees exactly what the editor shows");

		List<Paragraph> page = richPage();
		for (Alignment alignment : Alignment.values()) {
			for (Paragraph paragraph : page) {
				paragraph.setAlignment(alignment);
			}
			List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
			String written = LegacyCodec.encode(page, lines);

			List<String> wanted = expected(page, lines, false);
			List<String> got = resolved(written, false);

			if (wanted.size() != got.size()) {
				expect(false, alignment + ": the reader sees " + got.size()
						+ " characters where the editor shows " + wanted.size());
				continue;
			}
			int wrong = 0;
			String first = "";
			for (int i = 0; i < wanted.size(); i++) {
				if (!wanted.get(i).equals(got.get(i))) {
					if (wrong == 0) {
						first = "editor " + wanted.get(i) + " but reader " + got.get(i);
					}
					wrong++;
				}
			}
			expect(wrong == 0, alignment + ": " + wrong + " characters differ, first is " + first);
			if (wrong == 0) {
				System.out.println("  " + alignment + ": " + got.size() + " characters, all identical");
			}
		}
	}

	/**
	 * Every visible character the editor draws, with the style it draws it in.
	 *
	 * @param inkMatters whether "no colour of its own" and "black" are to be told apart. In a book
	 *                   they are the same ink and there is nothing to tell apart; on an item, where
	 *                   the server's torn-page plugin puts the text, black is a colour and no colour
	 *                   is the pale grey of a tooltip.
	 */
	private static List<String> expected(List<Paragraph> page, List<Layout.LaidLine> lines, boolean inkMatters) {
		List<String> out = new ArrayList<>();
		for (Layout.LaidLine line : lines) {
			Paragraph paragraph = page.get(line.paragraph);
			for (int i = 0; i < line.marker.length(); i++) {
				if (line.marker.charAt(i) != ' ') {
					out.add(mark(line.marker.charAt(i), line.markerStyle, inkMatters));
				}
			}
			for (int i = line.start; i < line.contentEnd; i++) {
				char c = paragraph.charAt(i);
				if (c != ' ') {
					out.add(mark(c, paragraph.styleAt(i), inkMatters));
				}
			}
			if (line.hyphen) {
				out.add(mark('-', paragraph.styleAt(Math.max(line.start, line.contentEnd - 1)), inkMatters));
			}
		}
		return out;
	}

	/**
	 * A page where formatting starts and stops, written and read back with the ink insisted upon.
	 *
	 * <p>Everything here is a single line, so every break on the page is one the encoder put in
	 * itself and knows the meaning of. That is the condition under which it may write {@code §r},
	 * and {@code §r} is the only code that says "nothing at all" rather than "black".
	 */
	private static void checkPlainStaysPlain() {
		section("Text nobody formatted comes out carrying nothing");

		List<Paragraph> page = new ArrayList<>();
		Paragraph first = new Paragraph();
		first.insert(first.length(), "123 ", QuillStyle.PLAIN);
		first.insert(first.length(), "синий", QuillStyle.PLAIN.withColor(0x5555FF));
		page.add(first);
		page.add(new Paragraph());

		Paragraph second = new Paragraph();
		second.insert(second.length(), "синий", QuillStyle.PLAIN.withColor(0x5555FF));
		second.insert(second.length(), " без форматирования", QuillStyle.PLAIN);
		page.add(second);

		Paragraph third = new Paragraph();
		third.insert(third.length(), "жирный", QuillStyle.PLAIN.withBold(true));
		third.insert(third.length(), " и снова обычный", QuillStyle.PLAIN);
		page.add(third);

		Paragraph title = new Paragraph("заголовок", QuillStyle.PLAIN.withBold(true).withColor(0xAA0000));
		title.setAlignment(Alignment.CENTER);
		page.add(title);
		page.add(new Paragraph("строка под ним", QuillStyle.PLAIN));

		// A paragraph the game will break up itself, coloured the whole way through, so that every
		// break inside it is one nobody wrote down. What is in force at such a break is carried to
		// the rest of the page – unless the blank it breaks at is written plain, which is invisible
		// and free, and is the only reason the plain line underneath is still plain.
		page.add(new Paragraph("длинный абзац в цвете который сам переносится игрой на вторую "
				+ "строку и даже на третью", QuillStyle.PLAIN.withColor(0x5555FF).withItalic(true)));
		page.add(new Paragraph("и обычная строка после него", QuillStyle.PLAIN));

		List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
		String written = LegacyCodec.encode(page, lines);
		List<String> wanted = expected(page, lines, true);
		List<String> got = resolved(written, true);

		if (wanted.size() != got.size()) {
			expect(false, "the reader sees " + got.size() + " characters where the editor shows "
					+ wanted.size());
			return;
		}
		int wrong = 0;
		String firstWrong = "";
		for (int i = 0; i < wanted.size(); i++) {
			if (!wanted.get(i).equals(got.get(i))) {
				if (wrong == 0) {
					firstWrong = "editor " + wanted.get(i) + " but reader " + got.get(i);
				}
				wrong++;
			}
		}
		expect(wrong == 0, wrong + " characters come out inked, first is " + firstWrong);
		if (wrong == 0) {
			System.out.println("  " + got.size() + " characters, and nothing black that was not asked to be");
			for (String line : written.split("\n", -1)) {
				System.out.println("  |" + line.replace(LegacyCodec.SECTION, '&') + "|");
			}
		}

		// And a book written by an older version of this mod, which had no way of saying "nothing"
		// and said "black" instead. It mends itself the next time the page is written, but only if
		// reading it does not take the black for a colour somebody chose.
		String old = "123 " + LegacyCodec.SECTION + "9синий\n\n"
				+ LegacyCodec.SECTION + "9синий" + LegacyCodec.SECTION + "0 без форматирования";
		List<Paragraph> read = LegacyCodec.decode(old);
		String again = LegacyCodec.encode(read, Layout.lay(read, Layout.Options.DEFAULT));
		expect(again.indexOf(LegacyCodec.SECTION + "0") < 0,
				"an old page keeps its black ink: " + again.replace(LegacyCodec.SECTION, '&'));
		expect(LegacyCodec.strip(again).equals(LegacyCodec.strip(old)),
				"an old page lost text on the way: " + again.replace(LegacyCodec.SECTION, '&'));
		System.out.println("  and an old page mends itself: |"
				+ again.replace(LegacyCodec.SECTION, '&').replace("\n", "¶") + "|");
	}

	/**
	 * Every visible character a reader ends up with, resolving the codes the way the game does.
	 *
	 * <p>Including the part of that which is easy to miss: {@code §r} does not mean plain. The game
	 * wraps a page by cutting it and handing the remainder on with the style that was in force at
	 * the cut, and it then renders that remainder with its reset style set to the same thing. So
	 * {@code §r} puts back whatever was on at the last line break, and a rule at the end of a line
	 * leaves its underline waiting to be restored by the next {@code §r}. That is modelled here,
	 * because a checker that treats {@code §r} as plain agrees with a mistake instead of catching it.
	 */
	private static List<String> resolved(String written, boolean inkMatters) {
		List<String> out = new ArrayList<>();
		boolean bold = false;
		boolean italic = false;
		boolean underlined = false;
		boolean strikethrough = false;
		boolean obfuscated = false;
		int colour = -1;
		// What §r goes back to: nothing at the top of the page, and the style at the last break
		// after that.
		boolean[] resetSwitches = new boolean[5];
		int resetColour = -1;

		for (int i = 0; i < written.length(); i++) {
			char c = written.charAt(i);
			if (c == LegacyCodec.SECTION) {
				if (i + 1 >= written.length()) {
					break;
				}
				char code = Character.toLowerCase(written.charAt(i + 1));
				int index = "0123456789abcdef".indexOf(code);
				if (index >= 0) {
					colour = index;
					bold = italic = underlined = strikethrough = obfuscated = false;
				} else {
					switch (code) {
						case 'l' -> bold = true;
						case 'o' -> italic = true;
						case 'n' -> underlined = true;
						case 'm' -> strikethrough = true;
						case 'k' -> obfuscated = true;
						case 'r' -> {
							colour = resetColour;
							bold = resetSwitches[0];
							italic = resetSwitches[1];
							underlined = resetSwitches[2];
							strikethrough = resetSwitches[3];
							obfuscated = resetSwitches[4];
						}
						default -> {
						}
					}
				}
				i++;
				continue;
			}
			if (c == '\n') {
				// The cut. Everything after it inherits what was on here, and that becomes what §r
				// means from now on.
				resetColour = colour;
				resetSwitches = new boolean[] { bold, italic, underlined, strikethrough, obfuscated };
				continue;
			}
			if (c == ' ') {
				continue;
			}
			// Black and "no colour of its own" are the same ink in a book, so unless the question is
			// about the ink itself they compare equal.
			out.add(c + "[" + (bold ? "b" : "") + (italic ? "i" : "") + (underlined ? "u" : "")
					+ (strikethrough ? "s" : "") + (obfuscated ? "o" : "")
					+ (inkMatters ? colour : Math.max(0, colour)) + "]");
		}
		return out;
	}

	private static String mark(char c, QuillStyle style, boolean inkMatters) {
		int colour = style.legacyColorIndex();
		return c + "[" + (style.bold() ? "b" : "") + (style.italic() ? "i" : "")
				+ (style.underlined() ? "u" : "") + (style.strikethrough() ? "s" : "")
				+ (style.obfuscated() ? "o" : "") + (inkMatters ? colour : Math.max(0, colour)) + "]";
	}

	/** A page with every switch and several colours crossing each other's paths. */
	private static List<Paragraph> richPage() {
		List<Paragraph> page = new ArrayList<>();

		Paragraph one = new Paragraph();
		one.insert(one.length(), "обычный ", QuillStyle.PLAIN);
		one.insert(one.length(), "жирный ", QuillStyle.PLAIN.withBold(true));
		one.insert(one.length(), "курсив ", QuillStyle.PLAIN.withItalic(true));
		one.insert(one.length(), "оба ", QuillStyle.PLAIN.withBold(true).withItalic(true));
		one.insert(one.length(), "снова обычный", QuillStyle.PLAIN);
		page.add(one);

		Paragraph two = new Paragraph();
		two.insert(two.length(), "красный ", QuillStyle.PLAIN.withColor(0xFF5555));
		two.insert(two.length(), "красный жирный ", QuillStyle.PLAIN.withColor(0xFF5555).withBold(true));
		two.insert(two.length(), "синий жирный ", QuillStyle.PLAIN.withColor(0x5555FF).withBold(true));
		two.insert(two.length(), "синий ", QuillStyle.PLAIN.withColor(0x5555FF));
		two.insert(two.length(), "без цвета", QuillStyle.PLAIN);
		page.add(two);

		Paragraph three = new Paragraph();
		three.insert(three.length(), "подчёркнутый ", QuillStyle.PLAIN.withUnderlined(true));
		three.insert(three.length(), "и зачёркнутый ",
				QuillStyle.PLAIN.withUnderlined(true).withStrikethrough(true));
		three.insert(three.length(), "только зачёркнутый ", QuillStyle.PLAIN.withStrikethrough(true));
		three.insert(three.length(), "чистый", QuillStyle.PLAIN);
		page.add(three);

		Paragraph four = new Paragraph();
		four.insert(four.length(), "зелёный подчёркнутый ",
				QuillStyle.PLAIN.withColor(0x55FF55).withUnderlined(true));
		four.insert(four.length(), "зелёный ", QuillStyle.PLAIN.withColor(0x55FF55));
		four.insert(four.length(), "жёлтый всё сразу",
				QuillStyle.PLAIN.withColor(0xFFFF55).withBold(true).withItalic(true).withUnderlined(true));
		page.add(four);

		Paragraph five = new Paragraph();
		five.insert(five.length(), "длинная строка которая точно переносится на вторую строку и там продолжает "
				+ "быть жирной до самого конца абзаца", QuillStyle.PLAIN.withBold(true).withColor(0xAA00AA));
		page.add(five);

		Paragraph six = new Paragraph("пункт списка", QuillStyle.PLAIN.withColor(0x00AAAA));
		six.setList(ListStyle.NUMBER);
		page.add(six);

		// A table with a rule under its heading. The heading is bold and the rule is underlined, so
		// the row after them is the place a leaked switch would show up first.
		page.addAll(TableBuilder.build(TableBuilder.parse("Товар|Цена\nХлеб|3\nЯблоко|1"),
				TableBuilder.Style.GRID, TableBuilder.Columns.AUTO, TableBuilder.Heading.BOLD));

		return page;
	}

	private static void checkPageBudget() {
		section("The counter agrees with what is written");
		Paragraph paragraph = new Paragraph(longRussian(), QuillStyle.PLAIN);
		paragraph.setAlignment(Alignment.CENTER);
		List<Paragraph> page = List.of(paragraph);
		List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
		int cost = LegacyCodec.cost(page, lines);
		int actual = LegacyCodec.encode(page, lines).length();
		expect(cost == actual, "the counter says " + cost + " and the page is " + actual);
		System.out.println("  centred paragraph: " + actual + " characters of the 1024");
	}

	// ---- material -------------------------------------------------------------------------------------

	private static List<String> samples() {
		return List.of(
				"Глава первая",
				"О том, как всё начиналось и чем это кончилось",
				"i",
				"Короткая",
				"Строка ровно такой длины, чтобы почти влезть в страницу целиком",
				longRussian());
	}

	private static String longRussian() {
		return "В тот вечер над рекой стоял туман, и никто из собравшихся не сказал ни слова, "
				+ "пока старик не поднял голову и не посмотрел на дальний берег, где горели огни.";
	}

	// ---- plumbing --------------------------------------------------------------------------------------

	/**
	 * How many lines the game itself makes of a page that carries no line breaks.
	 *
	 * <p>Greedy, breaking at the last blank that still fits and swallowing it, which is what
	 * {@code TextHandler} does. Used to hold this mod to the line count the reader would have got
	 * without it.
	 */
	private static int greedyLines(String text) {
		int lines = 1;
		float width = 0.0f;
		int lastSpace = -1;
		float widthAtSpace = 0.0f;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			float step = advance(c, false);
			if (width + step > PAGE && lastSpace >= 0) {
				lines++;
				width -= widthAtSpace + advance(' ', false);
				lastSpace = -1;
			}
			if (c == ' ') {
				lastSpace = i;
				widthAtSpace = width;
			}
			width += step;
		}
		return lines;
	}

	private static void section(String name) {
		System.out.println();
		System.out.println("== " + name);
	}

	private static void expect(boolean condition, String complaint) {
		checks++;
		if (!condition) {
			failures++;
			System.out.println("  FAILED: " + complaint);
		}
	}
}
