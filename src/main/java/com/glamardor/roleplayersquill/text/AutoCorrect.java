package com.glamardor.roleplayersquill.text;

import com.glamardor.roleplayersquill.config.QuillConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Putting typography right while it is being typed.
 *
 * <p>Everything here is the sort of thing a word processor does quietly: two hyphens become a dash,
 * straight quotes become the angled pair, three dots become one character. In a roleplay book it
 * matters more than in a letter, because the page is 114 pixels wide – a proper dash and a proper
 * ellipsis are narrower than what they replace, and every pixel on that page is one somebody spent.
 *
 * <p>Each rule can be turned off on its own. That is not decoration: an invented name should not be
 * capitalised, a keyboard shortcut written out in a guide should keep its hyphens, and whoever is
 * writing those knows which rule is in their way better than this file does.
 */
public final class AutoCorrect {
	private AutoCorrect() {
	}

	/**
	 * What to put in instead of the character just typed.
	 *
	 * @param back how many characters before the caret to take away first
	 * @param text what to write in their place, with the typed character already in it
	 */
	public record Fix(int back, String text) {
	}

	/**
	 * Looks at what is about to be typed and at what is already there.
	 *
	 * @param before the text of the paragraph up to the caret
	 * @param typed  the character the player pressed
	 * @return what to do instead, or null to type the character as it is
	 */
	@Nullable
	public static Fix apply(String before, char typed) {
		QuillConfig config = QuillConfig.get();
		if (!config.autoCorrect) {
			return null;
		}

		if (config.autoEllipsis && typed == '.' && before.endsWith("..")) {
			return new Fix(2, "…");
		}
		if (config.autoDashes && typed == '-') {
			if (before.endsWith("–")) {
				return new Fix(1, "—");
			}
			if (before.endsWith("-")) {
				return new Fix(1, "–");
			}
		}
		if (config.autoSigns && typed == ')') {
			String lower = before.toLowerCase(java.util.Locale.ROOT);
			if (lower.endsWith("(c")) {
				return new Fix(2, "©");
			}
			if (lower.endsWith("(r")) {
				return new Fix(2, "®");
			}
			if (lower.endsWith("(tm")) {
				return new Fix(3, "™");
			}
		}
		if (config.autoQuotes && typed == '"') {
			return new Fix(0, opens(before) ? "«" : "»");
		}
		if (config.autoApostrophe && typed == '\'') {
			return new Fix(0, "’");
		}
		if (config.autoCapitals && Character.isLowerCase(typed) && startsSentence(before)) {
			return new Fix(0, String.valueOf(Character.toUpperCase(typed)));
		}
		return null;
	}

	/**
	 * Whether a quotation mark here opens rather than closes.
	 *
	 * <p>By what is in front of it: nothing, a space or an opening bracket means it opens. Counting
	 * the quotes already on the line would be cleverer and would be wrong more often, because a book
	 * being written is full of half-finished sentences.
	 */
	private static boolean opens(String before) {
		if (before.isEmpty()) {
			return true;
		}
		char last = before.charAt(before.length() - 1);
		// An opening quote is not among the things that open one. Two quotation marks typed one
		// after the other are an empty quotation – «» – and reading the first as a reason to write
		// a second opening one gave ««, which is not a thing anybody types on purpose.
		return Character.isWhitespace(last) || "([{—–-".indexOf(last) >= 0;
	}

	/** Whether what comes next is the first letter of a sentence. */
	private static boolean startsSentence(String before) {
		int i = before.length() - 1;
		int spaces = 0;
		while (i >= 0 && Character.isWhitespace(before.charAt(i))) {
			i--;
			spaces++;
		}
		if (i < 0) {
			// The beginning of a paragraph – but only if the caret is really at the start, not after
			// a run of spaces somebody is using to lay something out by hand.
			return spaces == 0;
		}
		if (spaces == 0) {
			return false;
		}
		char end = before.charAt(i);
		return end == '.' || end == '!' || end == '?' || end == '…';
	}
}
