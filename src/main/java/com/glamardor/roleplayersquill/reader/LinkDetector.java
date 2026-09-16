package com.glamardor.roleplayersquill.reader;

import com.glamardor.roleplayersquill.config.QuillConfig;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.TextVisitFactory;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes an address written in a book clickable.
 *
 * <p>A book page is the same kind of text component the chat is, and the book reader already knows
 * how to follow a click event on one and to ask for confirmation before opening a browser. What it
 * does not do is notice that a page has an address written on it, because a page written by a
 * player is one flat string with no click event anywhere in it – and on a server without creative
 * mode there is no way for the writer to have put one there.
 *
 * <p>So the reader puts it there instead, on the way to the screen. Nothing is changed in the book
 * and nothing is sent anywhere: the text the page holds is the text it held, and for a player
 * without this mod the address is still simply readable. The confirmation is the game's own, which
 * is the point – an address in a book deserves exactly as much suspicion as one in the chat.
 */
public final class LinkDetector {
	private static final Pattern WITH_SCHEME =
			Pattern.compile("(?:https?)://[\\w\\-./?%&=+#:~@!$'()*,;\\[\\]]+", Pattern.CASE_INSENSITIVE);

	/**
	 * Without a scheme, only what is unmistakably an address.
	 *
	 * <p>Either it says {@code www} or it ends in one of the endings people actually write, because
	 * a rule loose enough to catch {@code site.ru} is loose enough to catch {@code и.т.д} and turn a
	 * Russian abbreviation into a link to nowhere.
	 */
	private static final Pattern WITHOUT_SCHEME = Pattern.compile(
			"(?:www\\.[\\w\\-]+(?:\\.[\\w\\-]+)+|[\\w\\-]+(?:\\.[\\w\\-]+)*"
					+ "\\.(?:com|org|net|io|ru|dev|me|gg|xyz|info|page|app|co|uk|de|fr|pl|ua|su|moe|wiki))"
					+ "(?:/[\\w\\-./?%&=+#:~@!$'()*,;\\[\\]]*)?",
			Pattern.CASE_INSENSITIVE);

	/** Punctuation that ends a sentence rather than an address. */
	private static final String TRAILING = ".,;:!?)]}»\"'";

	private static final int LINK_COLOUR = 0x1F4FA0;

	private LinkDetector() {
	}

	/**
	 * The address written at this point of a plain string, if there is one.
	 *
	 * <p>Used while the book is still being written, where the text is a string and not a {@link Text}
	 * yet. A bare address typed into a page carries no style of its own – it is only an address
	 * because it looks like one – so the only way to know whether a click landed on one is to look
	 * for the patterns again at the point that was clicked.
	 *
	 * @return the address, ready to open, or null if this is ordinary text
	 */
	@Nullable
	public static String addressAt(String text, int index) {
		QuillConfig config = QuillConfig.get();
		List<int[]> found = new ArrayList<>();
		collect(found, WITH_SCHEME.matcher(text));
		if (config.readerLinksWithoutScheme) {
			collect(found, WITHOUT_SCHEME.matcher(text));
		}
		for (int[] range : found) {
			if (index < range[0] || index >= range[1]) {
				continue;
			}
			String address = text.substring(range[0], range[1]);
			String url = address.toLowerCase(Locale.ROOT).startsWith("http") ? address : "https://" + address;
			if (isUsable(url)) {
				return url;
			}
		}
		return null;
	}

	/** The page, with any address in it turned into something the reader can click. */
	public static Text decorate(Text page) {
		QuillConfig config = QuillConfig.get();
		if (!config.readerLinks) {
			return page;
		}

		// Flattened with the codes already resolved into styles, so that splitting a run in the
		// middle cannot lose the formatting that a § earlier in it had switched on.
		StringBuilder plain = new StringBuilder();
		List<Style> styles = new ArrayList<>();
		TextVisitFactory.visitFormatted(page, Style.EMPTY, (index, style, codePoint) -> {
			for (char c : Character.toChars(codePoint)) {
				plain.append(c);
				styles.add(style);
			}
			return true;
		});

		String text = plain.toString();
		List<int[]> found = new ArrayList<>();
		collect(found, WITH_SCHEME.matcher(text));
		if (config.readerLinksWithoutScheme) {
			collect(found, WITHOUT_SCHEME.matcher(text));
		}
		if (found.isEmpty()) {
			return page;
		}

		boolean[] linked = new boolean[text.length()];
		String[] targets = new String[text.length()];
		for (int[] range : found) {
			String address = text.substring(range[0], range[1]);
			String url = address.toLowerCase(Locale.ROOT).startsWith("http") ? address : "https://" + address;
			if (!isUsable(url)) {
				continue;
			}
			for (int i = range[0]; i < range[1]; i++) {
				if (linked[i]) {
					// An overlap: the scheme-less pattern found the tail of one already matched.
					continue;
				}
				linked[i] = true;
				targets[i] = url;
			}
		}

		MutableText out = Text.empty();
		StringBuilder run = new StringBuilder();
		Style runStyle = null;
		String runTarget = null;
		for (int i = 0; i < text.length(); i++) {
			Style style = styles.get(i);
			String target = targets[i];
			if (runStyle != null && (!runStyle.equals(style) || !sameTarget(runTarget, target))) {
				out.append(styled(run.toString(), runStyle, runTarget));
				// Emptied, or the next run carries everything before it along – and since the run
				// that ends a page is the address, the address came out wearing the whole page.
				run.setLength(0);
			}
			runStyle = style;
			runTarget = target;
			run.append(text.charAt(i));
		}
		if (runStyle != null) {
			out.append(styled(run.toString(), runStyle, runTarget));
		}
		return out;
	}

	private static boolean sameTarget(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}

	private static Text styled(String run, Style style, String target) {
		Style applied = style;
		if (target != null) {
			applied = applied
					.withColor(TextColor.fromRgb(LINK_COLOUR))
					.withUnderline(true)
					.withClickEvent(new ClickEvent.OpenUrl(URI.create(target)))
					.withHoverEvent(new HoverEvent.ShowText(
							Text.translatable("roleplayersquill.reader.open", target)));
		}
		MutableText piece = Text.literal(run);
		piece.setStyle(applied);
		return piece;
	}

	private static void collect(List<int[]> found, Matcher matcher) {
		while (matcher.find()) {
			int start = matcher.start();
			int end = matcher.end();
			while (end > start && TRAILING.indexOf(matcher.group().charAt(end - start - 1)) >= 0) {
				end--;
			}
			if (end - start >= 4) {
				found.add(new int[] { start, end });
			}
		}
	}

	private static boolean isUsable(String url) {
		try {
			URI uri = URI.create(url);
			return uri.getHost() != null && !uri.getHost().isBlank();
		} catch (IllegalArgumentException error) {
			return false;
		}
	}
}
