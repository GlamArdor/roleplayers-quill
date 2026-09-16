import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import com.glamardor.roleplayersquill.text.QuillStyle;
import com.glamardor.roleplayersquill.text.Widths;

import java.nio.file.Path;
import java.util.List;

/**
 * Prints what a saved draft would actually be written to the book as.
 *
 * <p>For looking at a real book when a screenshot will not settle it: the exact string, with the
 * section signs shown as ampersands so they can be read, plus what the layout made of it. The
 * widths are a stand-in font, so the padding is not the real number of spaces – everything else,
 * and in particular where every code lands, is exactly what the game would be sent.
 *
 * <pre>
 * ./gradlew -q toolsClasspath
 * java -cp "build/classes/java/main;&lt;that&gt;" tools/DraftDump.java &lt;draft.json&gt; [page]
 * </pre>
 */
public final class DraftDump {

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("usage: DraftDump <draft.json> [page number]");
			System.exit(2);
		}
		Widths.useSource(DraftDump::advance);

		QuillDocument document = BookIO.readDocument(Path.of(args[0]));
		if (document == null) {
			System.err.println("could not read " + args[0]);
			System.exit(1);
			return;
		}

		int only = args.length > 1 ? Integer.parseInt(args[1]) : -1;
		System.out.println(document.pageCount() + " pages");

		for (int p = 0; p < document.pageCount(); p++) {
			if (only > 0 && p != only - 1) {
				continue;
			}
			List<Paragraph> page = document.page(p);
			List<Layout.LaidLine> lines = Layout.lay(page, Layout.Options.DEFAULT);
			String written = LegacyCodec.encode(page, lines);

			System.out.println();
			System.out.println("########## page " + (p + 1) + ": " + lines.size() + " lines, "
					+ written.length() + " characters");

			for (int i = 0; i < page.size(); i++) {
				Paragraph paragraph = page.get(i);
				System.out.println("  [" + i + "] " + paragraph.alignment() + " " + describe(paragraph));
			}

			System.out.println("  -- as written --");
			String[] out = written.split("\n", -1);
			for (int i = 0; i < out.length; i++) {
				System.out.println(String.format("  %2d%s |%s|", i + 1,
						i >= Layout.PAGE_LINES ? " CUT" : "    ", out[i].replace(LegacyCodec.SECTION, '&')));
			}

			System.out.println("  -- as a reader resolves it --");
			for (String line : resolve(written)) {
				System.out.println("     " + line);
			}
		}
	}

	/** The styles a reader ends up with, line by line, reading the way the game reads. */
	private static List<String> resolve(String page) {
		List<String> out = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		String style = "";
		StringBuilder marked = new StringBuilder();
		for (int i = 0; i < page.length(); i++) {
			char c = page.charAt(i);
			if (c == LegacyCodec.SECTION) {
				if (i + 1 >= page.length()) {
					break;
				}
				char code = Character.toLowerCase(page.charAt(i + 1));
				style = code == 'r' ? "" : style + code;
				i++;
				continue;
			}
			if (c == '\n') {
				out.add("[" + style + "] " + marked);
				marked.setLength(0);
				continue;
			}
			marked.append(c);
		}
		out.add("[" + style + "] " + marked);
		return out;
	}

	private static String describe(Paragraph paragraph) {
		StringBuilder out = new StringBuilder("\"" + paragraph.text() + "\"");
		QuillStyle running = null;
		int start = 0;
		for (int i = 0; i <= paragraph.length(); i++) {
			QuillStyle style = i < paragraph.length() ? paragraph.styleAt(i) : null;
			if (running != null && (style == null || !running.equals(style))) {
				if (!running.equals(QuillStyle.PLAIN)) {
					out.append("  <").append(start).append("-").append(i).append(" ")
							.append(running.underlined() ? "u" : "")
							.append(running.bold() ? "b" : "")
							.append(running.color() == QuillStyle.INHERIT ? ""
									: String.format("#%06X", running.color()))
							.append(">");
				}
				start = i;
			}
			running = style;
		}
		return out.toString();
	}

	private static float advance(int codePoint, boolean bold) {
		int base = codePoint == ' ' ? 4 : "il.,:;!|'".indexOf(codePoint) >= 0 ? 2 : 6;
		return base + (bold ? 1 : 0);
	}
}
