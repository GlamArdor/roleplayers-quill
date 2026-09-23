import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.Widths;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Opens books written without this mod and checks that the editor shows them the way the game does.
 *
 * <p>There is no stock of such books to test on, so they are made: the text of real drafts, set the
 * way somebody at the vanilla editor sets a page – one line per paragraph, bullets and numbers typed
 * by hand, a red line of spaces, a word in colour, a title centred by eye. Every page is then drawn
 * twice through a copy of {@code TextHandler.LineBreakingVisitor}: once as it was written, once as
 * this mod would write it back after reading it. Any glyph that lands somewhere else is a book that
 * looks different in somebody else's hands.
 *
 * <p>Widths are the game's own, read out of the font sheets in the client jar the way
 * {@code BitmapFont} reads them.
 *
 * <pre>
 * ./gradlew vanillaCheck -Pdrafts=&lt;folder of drafts&gt; [-Pseed=1]
 * </pre>
 */
public final class VanillaCheck {
	private static final float PAGE = 114.0f;
	private static final int PAGE_LINES = 14;
	private static final char SECTION = '§';
	private static final Map<Integer, Integer> ADVANCE = new HashMap<>();

	private VanillaCheck() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("usage: VanillaCheck <minecraft-client.jar> <drafts folder> [seed]");
			System.exit(2);
		}
		loadFont(Path.of(args[0]));
		Widths.useSource(VanillaCheck::advance);
		long seed = args.length > 2 ? Long.parseLong(args[2]) : 1L;

		List<List<String>> books = readDrafts(Path.of(args[1]));
		Random random = new Random(seed);
		Map<String, List<String>> faults = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();
		int pages = 0;
		for (List<String> book : books) {
			for (String page : vanillaPages(book, random)) {
				pages++;
				String fault = compare(page);
				if (fault != null) {
					String kind = fault.substring(0, fault.indexOf('\n'));
					counts.merge(kind, 1, Integer::sum);
					List<String> examples = faults.computeIfAbsent(kind, k -> new ArrayList<>());
					if (examples.size() < 3) {
						examples.add(fault);
					}
				}
			}
		}

		System.out.println(pages + " pages from " + books.size() + " books, seed " + seed);
		int bad = counts.values().stream().mapToInt(Integer::intValue).sum();
		System.out.println(bad + " pages look different in the editor");
		for (Map.Entry<String, List<String>> entry : faults.entrySet()) {
			System.out.println();
			System.out.println("== " + entry.getKey() + " – " + counts.get(entry.getKey()) + " pages");
			for (String example : entry.getValue()) {
				System.out.println(example.substring(example.indexOf('\n') + 1));
				System.out.println("  --");
			}
		}
		System.exit(bad == 0 ? 0 : 1);
	}

	// ---- the pages ------------------------------------------------------------------------------

	/** Sets a book's text the way a player at the vanilla editor would, and cuts it into pages. */
	private static List<String> vanillaPages(List<String> paragraphs, Random random) {
		List<String> pages = new ArrayList<>();
		StringBuilder page = new StringBuilder();
		int numbered = 0;
		for (String source : paragraphs) {
			String text = source.replace(String.valueOf(SECTION), "").replace('\t', ' ').strip();
			if (text.isEmpty() && random.nextInt(3) > 0) {
				continue;
			}
			String line;
			if (numbered > 0) {
				line = numbered++ + ". " + text;
				if (random.nextInt(4) == 0) {
					numbered = 0;
				}
			} else {
				line = dress(text, random);
				if (random.nextInt(12) == 0) {
					numbered = 1;
				}
			}
			for (String piece : random.nextInt(25) == 0 ? new String[] {line, ""} : new String[] {line}) {
				String candidate = page.isEmpty() ? piece : page + "\n" + piece;
				if (candidate.length() <= 1024 && gameLines(candidate).size() <= PAGE_LINES) {
					page.setLength(0);
					page.append(candidate);
				} else {
					if (!page.isEmpty()) {
						pages.add(page.toString());
					}
					page.setLength(0);
					if (piece.length() <= 1024 && gameLines(piece).size() <= PAGE_LINES) {
						page.append(piece);
					}
				}
			}
		}
		if (!page.isEmpty()) {
			pages.add(page.toString());
		}
		return pages;
	}

	/** One of the things people do to a line when there is nothing but spaces and codes to do it with. */
	private static String dress(String text, Random random) {
		String[] words = text.split(" ", -1);
		switch (random.nextInt(30)) {
			case 24:
				return "  • " + text;
			case 25:
				return text + " https://discord.gg/reign-roleplay-server-invite";
			case 26:
				return text + " ".repeat(20 + random.nextInt(40));
			case 27:
				return text.replaceFirst(" ", "      ");
			case 28:
				return "• " + SECTION + "l" + text;
			case 29:
				return " • " + text + " ";
			case 0, 1, 2:
				return "• " + text;
			case 3:
				return "•" + text;
			case 4:
				return "•  " + text;
			case 5:
				return "- " + text;
			case 6:
				return "– " + text;
			case 7: {
				return " ".repeat(1 + random.nextInt(6)) + text;
			}
			case 8: {
				// Centred by eye: about the right number of spaces, give or take one.
				float width = plainWidth(text);
				if (width >= PAGE) {
					return text;
				}
				int spaces = Math.max(1, Math.round((PAGE - width) / 2.0f / 4.0f) + random.nextInt(3) - 1);
				return " ".repeat(spaces) + text;
			}
			case 9: {
				int at = random.nextInt(words.length);
				words[at] = SECTION + "l" + words[at] + SECTION + "r";
				return String.join(" ", words);
			}
			case 10: {
				int at = random.nextInt(words.length);
				words[at] = SECTION + "9" + words[at] + SECTION + "r";
				return String.join(" ", words);
			}
			case 11:
				// A colour switched on and never off: it runs on into every line after it.
				return SECTION + "4" + text;
			case 12:
				return SECTION + "l" + text + SECTION + "r";
			case 13: {
				int at = random.nextInt(words.length);
				words[at] = SECTION + "n" + words[at] + SECTION + "r";
				return String.join(" ", words);
			}
			case 14:
				return text.replaceFirst(" ", "  ");
			case 15:
				return text.replace(" - ", " \u00A0- ").replaceFirst(" ", "\u00A0");
			case 16:
				return text + "   ";
			case 17:
				return SECTION + "n" + " ".repeat(8 + random.nextInt(20)) + SECTION + "r";
			case 18:
				return "    " + text;
			case 19:
				return SECTION + "l" + "• " + SECTION + "r" + text;
			case 20:
				return SECTION + "o" + text;
			default:
				return text;
		}
	}

	// ---- comparing ------------------------------------------------------------------------------

	private static String compare(String page) {
		List<Paragraph> read;
		String written;
		List<Layout.LaidLine> laid;
		try {
			read = LegacyCodec.decode(page);
			laid = Layout.lay(read, Layout.Options.DEFAULT);
			written = LegacyCodec.encode(read, laid);
		} catch (RuntimeException e) {
			return "crashes\n" + show(page) + "\n  " + e;
		}

		List<List<Glyph>> before = trim(draw(page));
		List<List<Glyph>> after = trim(draw(written));
		int editorLines = laid.size();
		while (editorLines > 0 && laid.get(editorLines - 1).contentEnd <= laid.get(editorLines - 1).start
				&& laid.get(editorLines - 1).marker.isEmpty()) {
			editorLines--;
		}
		if (editorLines != before.size()) {
			return "line count differs from the game\n" + show(page) + "\n  game " + before.size()
					+ " lines, editor " + editorLines + "\n" + side(before, after) + "\n" + editor(read, laid);
		}
		if (before.size() != after.size()) {
			return "written back it wraps differently\n" + show(page) + "\n" + side(before, after);
		}
		for (int line = 0; line < before.size(); line++) {
			List<Glyph> a = before.get(line);
			List<Glyph> b = after.get(line);
			if (!text(a).equals(text(b))) {
				return "a word moves to another line\n" + show(page) + "\n" + side(before, after);
			}
			for (int i = 0; i < a.size(); i++) {
				if (Math.abs(a.get(i).x - b.get(i).x) > 0.01f) {
					return "text shifts sideways\n" + show(page) + "\n  line " + (line + 1) + " '"
							+ a.get(i).c + "' at " + a.get(i).x + " -> " + b.get(i).x + "\n" + side(before, after);
				}
				if (!a.get(i).look.equals(b.get(i).look)) {
					return "formatting changes\n" + show(page) + "\n  line " + (line + 1) + " '" + a.get(i).c
							+ "' " + a.get(i).look + " -> " + b.get(i).look + "\n" + side(before, after);
				}
			}
		}
		return null;
	}

	/** Empty lines at the bottom of a page: nobody sees them, and nobody counts them. */
	private static List<List<Glyph>> trim(List<List<Glyph>> lines) {
		int last = lines.size();
		while (last > 0 && lines.get(last - 1).isEmpty()) {
			last--;
		}
		return new ArrayList<>(lines.subList(0, last));
	}

	/** The lines the editor lays out, as text. */
	private static String editor(List<Paragraph> read, List<Layout.LaidLine> laid) {
		StringBuilder out = new StringBuilder("  editor:");
		for (Layout.LaidLine line : laid) {
			Paragraph paragraph = read.get(line.paragraph);
			out.append("\n   [").append(line.leftPad.width()).append("] ").append(line.marker)
					.append(line.marker.isEmpty() ? "" : "+" + line.markerPad.width() + " ")
					.append(paragraph.text(), line.start, line.contentEnd).append('|');
		}
		return out.toString();
	}

	private static String text(List<Glyph> line) {
		StringBuilder out = new StringBuilder();
		for (Glyph glyph : line) {
			out.append(glyph.c);
		}
		return out.toString();
	}

	private static String show(String page) {
		return "  page: " + page.replace(SECTION, '&').replace("\n", "\\n").replace('\u00A0', '~');
	}

	private static String side(List<List<Glyph>> before, List<List<Glyph>> after) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < Math.max(before.size(), after.size()); i++) {
			String a = i < before.size() ? picture(before.get(i)) : "";
			String b = i < after.size() ? picture(after.get(i)) : "";
			out.append(String.format("  %-2d %s%n     %s%s%n", i + 1, a, b, a.equals(b) ? "" : "   <<"));
		}
		return out.toString().stripTrailing();
	}

	/** A line drawn to the pixel, two pixels to a column, so an offset shows. */
	private static String picture(List<Glyph> line) {
		StringBuilder out = new StringBuilder();
		for (Glyph glyph : line) {
			int column = Math.round(glyph.x / 2.0f);
			while (out.length() < column) {
				out.append(' ');
			}
			out.append(glyph.c == ' ' ? '_' : glyph.c);
		}
		return out.toString();
	}

	// ---- the game -------------------------------------------------------------------------------

	private record Look(boolean bold, boolean italic, boolean underlined, boolean struck, boolean obfuscated,
			int colour) {
		static final Look PLAIN = new Look(false, false, false, false, false, -1);

		Look code(char code) {
			int colour = "0123456789abcdef".indexOf(code);
			if (colour >= 0) {
				// Black is the ink a book is printed in: nobody can tell it from no colour.
				return new Look(false, false, false, false, false, colour == 0 ? -1 : colour);
			}
			return switch (code) {
				case 'l' -> new Look(true, italic, underlined, struck, obfuscated, this.colour);
				case 'o' -> new Look(bold, true, underlined, struck, obfuscated, this.colour);
				case 'n' -> new Look(bold, italic, true, struck, obfuscated, this.colour);
				case 'm' -> new Look(bold, italic, underlined, true, obfuscated, this.colour);
				case 'k' -> new Look(bold, italic, underlined, struck, true, this.colour);
				case 'r' -> PLAIN;
				default -> this;
			};
		}

		@Override
		public String toString() {
			return (bold ? "bold " : "") + (italic ? "italic " : "") + (underlined ? "underlined " : "")
					+ (struck ? "struck " : "") + (obfuscated ? "obfuscated " : "")
					+ (colour < 0 ? "ink" : "colour " + colour);
		}
	}

	private record Glyph(char c, float x, Look look) {
	}

	private record Line(int start, int end, Look look) {
	}

	/** Where TextHandler.wrapLines cuts a page, and the style each line starts in. */
	private static List<Line> gameLines(String text) {
		List<Line> lines = new ArrayList<>();
		int i = 0;
		int n = text.length();
		Look style = Look.PLAIN;
		while (i < n) {
			// LineBreakingVisitor over visitFormatted, starting at i.
			float total = 0.0f;
			boolean nonEmpty = false;
			int lastSpace = -1;
			Look lastSpaceLook = Look.PLAIN;
			int end = -1;
			Look endLook = style;
			Look current = style;
			for (int k = i; k < n; k++) {
				char c = text.charAt(k);
				if (c == SECTION) {
					if (k + 1 >= n) {
						break;
					}
					current = current.code(Character.toLowerCase(text.charAt(k + 1)));
					k++;
					continue;
				}
				if (c == '\n') {
					end = k;
					endLook = current;
					break;
				}
				if (c == ' ') {
					lastSpace = k;
					lastSpaceLook = current;
				}
				float f = advance(c, current.bold);
				total += f;
				if (nonEmpty && total > PAGE) {
					if (lastSpace != -1) {
						end = lastSpace;
						endLook = lastSpaceLook;
					} else {
						end = k;
						endLook = current;
					}
					break;
				}
				nonEmpty |= f != 0.0f;
			}
			if (end < 0) {
				lines.add(new Line(i, n, style));
				break;
			}
			lines.add(new Line(i, end, style));
			char c = text.charAt(end);
			int next = c == '\n' || c == ' ' ? end + 1 : end;
			if (next <= i) {
				next = i + 1;
			}
			i = next;
			style = endLook;
		}
		if (text.isEmpty()) {
			lines.add(new Line(0, 0, Look.PLAIN));
		}
		return lines;
	}

	/** Every glyph the reader would see, line by line, with where it stands and how it looks. */
	private static List<List<Glyph>> draw(String text) {
		List<List<Glyph>> out = new ArrayList<>();
		for (Line line : gameLines(text)) {
			List<Glyph> glyphs = new ArrayList<>();
			float x = 0.0f;
			Look look = line.look;
			for (int k = line.start; k < line.end; k++) {
				char c = text.charAt(k);
				if (c == SECTION) {
					if (k + 1 < text.length()) {
						look = look.code(Character.toLowerCase(text.charAt(k + 1)));
					}
					k++;
					continue;
				}
				// A blank is only seen when something is drawn along it.
				if (c != ' ' && c != ' ' || look.underlined || look.struck) {
					glyphs.add(new Glyph(c == '\u00A0' ? ' ' : c, x,
							c == ' ' || c == '\u00A0' ? look : new Look(look.bold, look.italic, look.underlined,
									look.struck, look.obfuscated, look.colour)));
				}
				x += advance(c, look.bold);
			}
			// Trailing blanks cost nothing anybody can see, unless they are drawn on.
			out.add(glyphs);
		}
		return out;
	}

	// ---- the font -------------------------------------------------------------------------------

	private static float advance(int codePoint, boolean bold) {
		Integer base = ADVANCE.get(codePoint);
		float width = base != null ? base : 5.0f;
		if (codePoint == ' ' || codePoint == '\u00A0' && base == null) {
			width = 4.0f;
		}
		return width + (bold ? 1.0f : 0.0f);
	}

	private static float plainWidth(String text) {
		float width = 0.0f;
		for (int i = 0; i < text.length(); i++) {
			width += advance(text.charAt(i), false);
		}
		return width;
	}

	private static void loadFont(Path jar) throws Exception {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			JsonObject include = JsonParser.parseReader(reader(zip, "assets/minecraft/font/include/default.json"))
					.getAsJsonObject();
			ADVANCE.put((int) ' ', 4);
			ADVANCE.put(0x200C, 0);
			for (JsonElement element : include.getAsJsonArray("providers")) {
				JsonObject provider = element.getAsJsonObject();
				if (!"bitmap".equals(provider.get("type").getAsString())) {
					continue;
				}
				String file = provider.get("file").getAsString().replace("minecraft:", "");
				int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
				BufferedImage image;
				try (InputStream in = zip.getInputStream(zip.getEntry("assets/minecraft/textures/" + file))) {
					image = ImageIO.read(in);
				}
				JsonArray rows = provider.getAsJsonArray("chars");
				int[][] grid = new int[rows.size()][];
				for (int r = 0; r < rows.size(); r++) {
					grid[r] = rows.get(r).getAsString().codePoints().toArray();
				}
				int cellW = image.getWidth() / grid[0].length;
				int cellH = image.getHeight() / grid.length;
				float scale = (float) height / cellH;
				for (int r = 0; r < grid.length; r++) {
					for (int col = 0; col < grid[r].length; col++) {
						int cp = grid[r][col];
						if (cp == 0 || ADVANCE.containsKey(cp)) {
							continue;
						}
						int q = startX(image, cellW, cellH, col, r);
						ADVANCE.put(cp, (int) (0.5 + q * scale) + 1);
					}
				}
			}
		}
	}

	private static int startX(BufferedImage image, int cellW, int cellH, int col, int row) {
		int i;
		for (i = cellW - 1; i >= 0; i--) {
			int x = col * cellW + i;
			for (int k = 0; k < cellH; k++) {
				if ((image.getRGB(x, row * cellH + k) >>> 24) != 0) {
					return i + 1;
				}
			}
		}
		return i + 1;
	}

	private static InputStreamReader reader(ZipFile zip, String name) throws Exception {
		ZipEntry entry = Objects.requireNonNull(zip.getEntry(name), name);
		return new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8);
	}

	// ---- the drafts -----------------------------------------------------------------------------

	private static List<List<String>> readDrafts(Path folder) throws Exception {
		List<List<String>> books = new ArrayList<>();
		try (Stream<Path> files = Files.list(folder)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				JsonElement root;
				try {
					root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				} catch (Exception e) {
					continue;
				}
				if (!root.isJsonObject() || !root.getAsJsonObject().has("pages")) {
					continue;
				}
				List<String> paragraphs = new ArrayList<>();
				for (JsonElement page : root.getAsJsonObject().getAsJsonArray("pages")) {
					for (JsonElement paragraph : page.getAsJsonArray()) {
						paragraphs.add(paragraph.getAsJsonObject().get("text").getAsString());
					}
				}
				books.add(paragraphs);
			}
		}
		return books;
	}
}
