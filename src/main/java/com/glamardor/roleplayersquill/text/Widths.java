package com.glamardor.roleplayersquill.text;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.text.StringVisitable;

import java.util.HashMap;
import java.util.Map;

/**
 * How wide a character is, asked of the game rather than assumed.
 *
 * <p>Everything this mod does to make a page look laid out comes down to arithmetic on glyph
 * advances, and the numbers have to be the game's own: a resource pack is free to give the font a
 * different space, and a player who turns on the unicode font changes every letter at once. So the
 * width of a glyph is measured once through the same {@code TextHandler} the book renderer will use
 * and then remembered, and the memory is thrown away whenever the font behind it might have moved.
 *
 * <h2>The space trick</h2>
 *
 * <p>A page leaves the client as a string, so the only way to push text to the right is to put
 * something in front of it, and the only thing that can go in front of it without being seen is a
 * space. One space is four pixels. A <em>bold</em> space is five, because bold widens every glyph
 * by one – and {@code §l} costs two characters once, not once per space. Mixing the two gives every
 * width of the form {@code 4n + b} with {@code b} no larger than {@code n}, which is every width
 * from 8 upwards and all but three below it. That is what makes centring, right alignment and
 * justification land on the pixel instead of near it.
 */
public final class Widths {
	private static final Map<Long, Float> CACHE = new HashMap<>();
	private static TextRenderer lastRenderer;

	/**
	 * Where the numbers come from.
	 *
	 * <p>The game, normally. The seam exists so that the layout arithmetic – which is the part of
	 * this mod that is easy to get quietly wrong – can be checked against a font whose widths are
	 * known, without a window, a world or a render thread. See {@code tools/LayoutCheck.java}.
	 */
	@FunctionalInterface
	public interface Source {
		float advance(int codePoint, boolean bold);
	}

	private static Source source;

	private Widths() {
	}

	/** Measures against something other than the game's font. For the checks in {@code tools}. */
	public static void useSource(Source replacement) {
		source = replacement;
		CACHE.clear();
	}

	/** Forgets every measurement. Call when the font may have changed under us. */
	public static void clear() {
		CACHE.clear();
		lastRenderer = null;
	}

	private static TextRenderer renderer() {
		TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
		if (renderer != lastRenderer) {
			CACHE.clear();
			lastRenderer = renderer;
		}
		return renderer;
	}

	/** The advance of one code point, bold or not, in the font the book will actually be read in. */
	/**
	 * The tab, which stands for a leader rather than for a character.
	 *
	 * <p>A book cannot hold one – the game throws it out along with everything else below a space –
	 * so it is free for the editor to use as a mark of its own. Where it stands, the line is filled
	 * with dots out to whatever follows, and the dots are worked out when the line is laid, not
	 * when it is typed. It is never written to a page: by then it has become the dots.
	 */
	public static final char LEADER = '\t';

	/**
	 * The blank that holds two words together, which is an instruction and not a character.
	 *
	 * <p>A book has no such thing. The game breaks a line at a space and at nothing else, so "10 kg"
	 * is two words to it however much it is one to a reader. What goes on the page is therefore an
	 * ordinary space, and the holding-together is done the only way it can be: the paragraph is
	 * written with the break where this mod put it rather than left to the game, so the line ends
	 * before the pair rather than inside it.
	 *
	 * <p>It is as wide as a space and measured as one, because that is what it will be by the time
	 * anybody reads it.
	 */
	public static final char NOBREAK = '\u00A0';

	public static float advance(int codePoint, boolean bold) {
		if (codePoint == LEADER) {
			// No width of its own. What fills the gap is decided by the line it ends up on.
			return 0.0f;
		}
		if (codePoint == NOBREAK) {
			codePoint = ' ';
		}
		if (source != null) {
			return source.advance(codePoint, bold);
		}
		TextRenderer renderer = renderer();
		long key = (codePoint & 0xFFFFFFFFL) | (bold ? 1L << 40 : 0L);
		Float cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		String single = new String(Character.toChars(codePoint));
		float width = renderer.getTextHandler()
				.getWidth(StringVisitable.styled(single, Style.EMPTY.withBold(bold)));
		CACHE.put(key, width);
		return width;
	}

	public static float space() {
		return advance(' ', false);
	}

	public static float boldSpace() {
		return advance(' ', true);
	}

	/** How much one bold space buys over a plain one. One pixel, unless a pack says otherwise. */
	public static float boldGain() {
		return Math.max(0.0f, boldSpace() - space());
	}

	/** The width of a whole string in one style, which is what a marker or a hyphen needs. */
	public static float widthOf(String text, boolean bold) {
		float total = 0.0f;
		int i = 0;
		while (i < text.length()) {
			int codePoint = text.codePointAt(i);
			total += advance(codePoint, bold);
			i += Character.charCount(codePoint);
		}
		return total;
	}

	/**
	 * A run of spaces as close to {@code target} pixels as spaces can get, never over.
	 *
	 * @return how many spaces to write and how many of them to write in bold
	 */
	public static Padding pad(float target) {
		float s = space();
		if (s <= 0.0f || target < s) {
			return Padding.NONE;
		}
		float gain = boldGain();
		int count = (int) Math.floor((target + 0.001f) / s);
		int bold = 0;
		if (gain > 0.0f) {
			float remainder = target - count * s;
			bold = Math.min(count, (int) Math.floor((remainder + 0.001f) / gain));
		}
		return new Padding(count, bold, count * s + bold * gain);
	}

	/**
	 * Spaces to fill a gap of at least one space, used between the words of a justified line.
	 *
	 * @param target the whole width the gap should take, never below one plain space
	 */
	public static Padding gap(float target) {
		Padding padding = pad(Math.max(target, space()));
		return padding.count() == 0 ? new Padding(1, 0, space()) : padding;
	}

	/** @param width what the spaces actually come to, which may be a pixel or two under the target */
	public record Padding(int count, int bold, float width) {
		public static final Padding NONE = new Padding(0, 0, 0.0f);

		public boolean isEmpty() {
			return count == 0;
		}

		/** How many characters of the thousand-odd a page allows this costs. */
		public int cost() {
			return count + (bold > 0 ? 2 : 0);
		}
	}
}
