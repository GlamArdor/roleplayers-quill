package com.glamardor.roleplayersquill.text;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Objects;

/**
 * Everything the editor can say about one character.
 *
 * <p>Kept separate from vanilla's {@link Style} on purpose. A book page leaves the client as a
 * plain string, so most of what is written here has to survive as {@code §} codes, and those can
 * carry five switches and sixteen colours and nothing else. The rest – a real colour, a link, a
 * tooltip – only exists when the page can be sent as a text component instead, which is what
 * {@link com.glamardor.roleplayersquill.book.RichWriter} is for. Holding both in one type means the
 * editor never has to know which of the two it is writing for until the moment it writes.
 *
 * @param color   0xRRGGBB, or {@link #INHERIT} for the book's own ink
 * @param url     an address to open, with the same confirmation the chat asks for
 * @param command a command the reader runs by clicking, as the reader
 * @param page    a page of this same book to jump to, 1-based; 0 for none
 */
public record QuillStyle(
		boolean bold,
		boolean italic,
		boolean underlined,
		boolean strikethrough,
		boolean obfuscated,
		int color,
		@Nullable String url,
		@Nullable String hover,
		@Nullable String command,
		@Nullable String copy,
		int page) {

	/** No colour of its own: whatever the book is printed in. */
	public static final int INHERIT = -1;

	public static final QuillStyle PLAIN =
			new QuillStyle(false, false, false, false, false, INHERIT, null, null, null, null, 0);

	/** The sixteen colours a {@code §} code can name, in code order. */
	public static final Formatting[] LEGACY_COLORS = {
			Formatting.BLACK, Formatting.DARK_BLUE, Formatting.DARK_GREEN, Formatting.DARK_AQUA,
			Formatting.DARK_RED, Formatting.DARK_PURPLE, Formatting.GOLD, Formatting.GRAY,
			Formatting.DARK_GRAY, Formatting.BLUE, Formatting.GREEN, Formatting.AQUA,
			Formatting.RED, Formatting.LIGHT_PURPLE, Formatting.YELLOW, Formatting.WHITE
	};

	public QuillStyle withBold(boolean value) {
		return new QuillStyle(value, italic, underlined, strikethrough, obfuscated, color, url, hover, command, copy, page);
	}

	public QuillStyle withItalic(boolean value) {
		return new QuillStyle(bold, value, underlined, strikethrough, obfuscated, color, url, hover, command, copy, page);
	}

	public QuillStyle withUnderlined(boolean value) {
		return new QuillStyle(bold, italic, value, strikethrough, obfuscated, color, url, hover, command, copy, page);
	}

	public QuillStyle withStrikethrough(boolean value) {
		return new QuillStyle(bold, italic, underlined, value, obfuscated, color, url, hover, command, copy, page);
	}

	public QuillStyle withObfuscated(boolean value) {
		return new QuillStyle(bold, italic, underlined, strikethrough, value, color, url, hover, command, copy, page);
	}

	public QuillStyle withColor(int rgb) {
		return new QuillStyle(bold, italic, underlined, strikethrough, obfuscated, rgb, url, hover, command, copy, page);
	}

	public QuillStyle withUrl(@Nullable String value) {
		return new QuillStyle(bold, italic, underlined, strikethrough, obfuscated, color, value, hover, command, copy, page);
	}

	public QuillStyle withHover(@Nullable String value) {
		return new QuillStyle(bold, italic, underlined, strikethrough, obfuscated, color, url, value, command, copy, page);
	}

	public QuillStyle withCommand(@Nullable String value) {
		return new QuillStyle(bold, italic, underlined, strikethrough, obfuscated, color, url, hover, value, copy, page);
	}

	public QuillStyle withCopy(@Nullable String value) {
		return new QuillStyle(bold, italic, underlined, strikethrough, obfuscated, color, url, hover, command, value, page);
	}

	public QuillStyle withPage(int value) {
		return new QuillStyle(bold, italic, underlined, strikethrough, obfuscated, color, url, hover, command, copy, value);
	}

	/** Drops everything a click or a tooltip adds, leaving the look alone. */
	public QuillStyle withoutInteraction() {
		return new QuillStyle(bold, italic, underlined, strikethrough, obfuscated, color, null, null, null, null, 0);
	}

	/** Everything but the interaction, which is what the format brush copies. */
	public QuillStyle lookOnly() {
		return withoutInteraction();
	}

	public boolean hasInteraction() {
		return url != null || hover != null || command != null || copy != null || page != 0;
	}

	/** True when a run of spaces styled like this would look like anything at all. */
	public boolean marksBlanks() {
		return underlined || strikethrough || obfuscated;
	}

	/**
	 * The formatting that survives a {@code §} round trip: the five switches and the nearest of the
	 * sixteen named colours.
	 */
	public QuillStyle legacyPart() {
		int snapped = color == INHERIT ? INHERIT : nearestLegacy().getColorValue();
		return new QuillStyle(bold, italic, underlined, strikethrough, obfuscated, snapped, null, null, null, null, 0);
	}

	/** Whether two styles need a {@code §} code between them at all. */
	public boolean sameLegacy(QuillStyle other) {
		return bold == other.bold
				&& italic == other.italic
				&& underlined == other.underlined
				&& strikethrough == other.strikethrough
				&& obfuscated == other.obfuscated
				&& legacyColorIndex() == other.legacyColorIndex();
	}

	/** Index into {@link #LEGACY_COLORS}, or -1 when the ink is to be left alone. */
	public int legacyColorIndex() {
		if (color == INHERIT) {
			return -1;
		}
		int best = 0;
		int bestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < LEGACY_COLORS.length; i++) {
			Integer value = LEGACY_COLORS[i].getColorValue();
			if (value == null) {
				continue;
			}
			int distance = distance(color, value);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	public Formatting nearestLegacy() {
		int index = legacyColorIndex();
		return index < 0 ? Formatting.BLACK : LEGACY_COLORS[index];
	}

	/**
	 * Plain squared distance in RGB. Not a perceptual match, and does not need to be: the sixteen
	 * candidates are far enough apart that anything closer would pick the same one.
	 */
	private static int distance(int a, int b) {
		int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
		int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF);
		return dr * dr + dg * dg + db * db;
	}

	/** What the editor draws with. Always a real colour, because the editor has no ink of its own. */
	public Style toVanilla(int defaultColor) {
		Style style = Style.EMPTY
				.withBold(bold)
				.withItalic(italic)
				.withUnderline(underlined)
				.withStrikethrough(strikethrough)
				.withObfuscated(obfuscated)
				.withColor(color == INHERIT ? defaultColor : color);
		return style;
	}

	/**
	 * What a written page carries when it can be sent as a component: the same look, plus the click
	 * and the tooltip that {@code §} codes have no room for.
	 *
	 * <p>Says only what the author said, and nothing more. A run left alone gets no colour and no
	 * switch set to false: in the book that draws exactly as black on parchment, and anywhere the
	 * book's text is carried to – a torn page's lore, a sign, a message in chat – it takes the ink
	 * of the place it lands in instead of arriving as black on black. Spelling out a default is how
	 * a page stops being ordinary text without looking any different.
	 */
	public Style toRichVanilla() {
		Style style = Style.EMPTY;
		if (bold) {
			style = style.withBold(true);
		}
		if (italic) {
			style = style.withItalic(true);
		}
		if (underlined) {
			style = style.withUnderline(true);
		}
		if (strikethrough) {
			style = style.withStrikethrough(true);
		}
		if (obfuscated) {
			style = style.withObfuscated(true);
		}
		if (color != INHERIT) {
			style = style.withColor(color);
		}
		ClickEvent click = null;
		if (url != null) {
			try {
				click = new ClickEvent.OpenUrl(URI.create(url));
			} catch (IllegalArgumentException ignored) {
				// A link nobody can parse is a link nobody should be offered.
			}
		} else if (command != null) {
			click = new ClickEvent.RunCommand(command);
		} else if (copy != null) {
			click = new ClickEvent.CopyToClipboard(copy);
		} else if (page > 0) {
			click = new ClickEvent.ChangePage(page);
		}
		if (click != null) {
			style = style.withClickEvent(click);
		}
		if (hover != null) {
			style = style.withHoverEvent(new HoverEvent.ShowText(LegacyCodec.toText(hover)));
		}
		return style;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof QuillStyle that)) {
			return false;
		}
		return bold == that.bold
				&& italic == that.italic
				&& underlined == that.underlined
				&& strikethrough == that.strikethrough
				&& obfuscated == that.obfuscated
				&& color == that.color
				&& page == that.page
				&& Objects.equals(url, that.url)
				&& Objects.equals(hover, that.hover)
				&& Objects.equals(command, that.command)
				&& Objects.equals(copy, that.copy);
	}

	@Override
	public int hashCode() {
		return Objects.hash(bold, italic, underlined, strikethrough, obfuscated, color, url, hover, command, copy, page);
	}
}
