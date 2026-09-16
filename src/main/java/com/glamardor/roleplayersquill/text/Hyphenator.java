package com.glamardor.roleplayersquill.text;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where a word may be broken across two lines.
 *
 * <p>A book page is 114 pixels wide, which is about twenty Cyrillic letters, and Russian words are
 * long. Without hyphenation a page of prose loses a third of its room to the ragged edge, and
 * justification turns the gaps between words into corridors. With it the text sits close.
 *
 * <p>Two engines. The built-in one works from the rules of the language – vowels, consonants, and
 * the letters that may not start a line – which is enough to be right nearly always and never
 * embarrassing. The other reads TeX hyphenation patterns, if the player has put a file where this
 * class looks, and is right every time because those patterns were built from dictionaries. The
 * file is nobody's job to provide: the rules are the default and the patterns are a refinement.
 *
 * <p>Patterns go in {@code config/roleplayersquill/hyphenation/<language>.pat}, one pattern per
 * line, in Liang's notation – the same content as {@code hyph-ru.tex} or {@code hyph-en-us.tex}
 * with the TeX wrapper stripped.
 */
public final class Hyphenator {
	/** Letters that may never be moved to the start of a line: they belong to what came before. */
	private static final String RU_TAIL_ONLY = "ьъй";
	private static final String RU_VOWELS = "аеёиоуыэюя";
	private static final String EN_VOWELS = "aeiouy";
	/** Pairs that stand for one sound, so a break between them would be read as two. */
	private static final String[] EN_DIGRAPHS = { "ch", "sh", "th", "ph", "wh", "gh", "ck", "ng", "qu" };
	private static final String[] EN_PREFIXES = {
			"under", "inter", "trans", "over", "semi", "anti", "non", "mis", "pre", "dis",
			"sub", "out", "up", "re", "un", "in", "im", "en", "em"
	};
	private static final String[] EN_SUFFIXES = {
			"ation", "ition", "ously", "ative", "tion", "sion", "ment", "ness", "able", "ible",
			"less", "ful", "ing", "est", "ity", "ies", "ous", "ly", "er", "or", "al"
	};

	private static final Map<String, Patterns> LOADED = new HashMap<>();

	private Hyphenator() {
	}

	/** Throws away any pattern file already read, so a freshly dropped one is picked up. */
	public static void reload() {
		LOADED.clear();
	}

	/**
	 * The places {@code word} may be broken, as offsets into it: an offset of {@code n} means the
	 * first {@code n} characters stay on this line and a hyphen is drawn after them.
	 *
	 * <p>Offsets are returned in increasing order and already obey the minimums, so a caller can
	 * simply take the largest one that fits.
	 *
	 * @param minBefore how many characters must be left behind, at the very least
	 * @param minAfter  how many must be carried over
	 */
	public static List<Integer> points(String word, int minBefore, int minAfter) {
		List<Integer> empty = List.of();
		if (word.length() < minBefore + minAfter) {
			return empty;
		}
		Script script = scriptOf(word);
		if (script == Script.MIXED) {
			return empty;
		}
		Patterns patterns = patterns(script == Script.CYRILLIC ? "ru" : "en");
		List<Integer> found = patterns != null
				? patterns.points(word, minBefore, minAfter)
				: (script == Script.CYRILLIC
						? russian(word, minBefore, minAfter)
						: english(word, minBefore, minAfter));
		return found;
	}

	// ---- Russian --------------------------------------------------------------------------------

	/**
	 * The school rules, in the order a reader would apply them: cut between syllables, never leave
	 * or carry a single letter, never tear {@code ь}, {@code ъ} or {@code й} off what they soften.
	 *
	 * <p>Where a run of consonants sits between two vowels, the break goes after the first of them –
	 * {@code сес-тра}, {@code сол-нце}. A single consonant goes with the vowel that follows it,
	 * {@code во-да}. Both are permitted spellings of the rule; the rule itself allows more than one
	 * answer, and picking the same one every time is what matters.
	 */
	private static List<Integer> russian(String word, int minBefore, int minAfter) {
		String lower = word.toLowerCase(Locale.ROOT);
		int n = lower.length();
		boolean[] vowel = new boolean[n];
		for (int i = 0; i < n; i++) {
			vowel[i] = RU_VOWELS.indexOf(lower.charAt(i)) >= 0;
		}

		List<Integer> points = new ArrayList<>();
		int i = 0;
		while (i < n) {
			if (!vowel[i]) {
				i++;
				continue;
			}
			// Consonants between this vowel and the next one.
			int run = i + 1;
			while (run < n && !vowel[run]) {
				run++;
			}
			if (run >= n) {
				break;
			}
			int consonants = run - (i + 1);
			int cut = switch (consonants) {
				case 0 -> i + 1;
				case 1 -> i + 1;
				default -> i + 2;
			};
			// ь, ъ and й stay with the letter they belong to, so the cut slides past them.
			while (cut < n && RU_TAIL_ONLY.indexOf(lower.charAt(cut)) >= 0) {
				cut++;
			}
			if (cut >= minBefore && n - cut >= minAfter && (points.isEmpty() || points.get(points.size() - 1) != cut)) {
				points.add(cut);
			}
			i = run;
		}
		return points;
	}

	// ---- English --------------------------------------------------------------------------------

	/**
	 * English, cautiously.
	 *
	 * <p>English spelling does not say where its own syllables are – that is why TeX needs five
	 * thousand patterns for it and none for Finnish – so the rules here aim to be quiet rather than
	 * clever. Two letters must stay behind and three must go on, which is the printers' minimum, and
	 * two break points are never allowed within three letters of each other. Left to itself the
	 * vowel-and-consonant rule offers a break after every single vowel and turns {@code hyphenation}
	 * into {@code hyph-e-n-a-ti-on}; the spacing rule is what stops it.
	 */
	private static List<Integer> english(String word, int minBefore, int minAfter) {
		String lower = word.toLowerCase(Locale.ROOT);
		int n = lower.length();
		int before = Math.max(minBefore, 2);
		int after = Math.max(minAfter, 3);
		// Each candidate carries how much it is to be trusted. A prefix, a suffix and a split
		// between two consonants are nearly always right; a split around a single consonant is a
		// coin toss – "ti-ger" or "tig-er" depends on a vowel length the spelling does not record –
		// so it only gets used where a better answer has not already taken the space.
		List<int[]> candidates = new ArrayList<>();

		for (String prefix : EN_PREFIXES) {
			if (lower.startsWith(prefix) && prefix.length() >= before && n - prefix.length() >= after) {
				candidates.add(new int[] { prefix.length(), 0 });
				break;
			}
		}

		boolean[] vowel = new boolean[n];
		for (int i = 0; i < n; i++) {
			vowel[i] = EN_VOWELS.indexOf(lower.charAt(i)) >= 0;
		}
		for (int i = 0; i < n - 1; i++) {
			if (!vowel[i]) {
				continue;
			}
			int run = i + 1;
			while (run < n && !vowel[run]) {
				run++;
			}
			if (run >= n) {
				break;
			}
			int consonants = run - (i + 1);
			int cut = consonants <= 1 ? i + 1 : i + 2;
			// A pair that stands for one sound is not split; the break goes in front of it, which
			// is how "hyphenation" becomes "hy-phenation" rather than "hyph-enation".
			if (consonants >= 2 && splitsDigraph(lower, cut)) {
				cut--;
			}
			if (cut >= before && n - cut >= after) {
				candidates.add(new int[] { cut, consonants >= 2 ? 0 : 1 });
			}
			i = run - 1;
		}

		for (String suffix : EN_SUFFIXES) {
			if (lower.endsWith(suffix)) {
				int cut = n - suffix.length();
				if (cut >= before && suffix.length() >= after) {
					candidates.add(new int[] { cut, 0 });
				}
				break;
			}
		}

		return resolve(candidates, 3);
	}

	/**
	 * Picks the break points that do not crowd each other, taking the trustworthy ones first.
	 *
	 * <p>Two breaks three letters apart would mean a syllable of two, which English does not do
	 * often enough to be worth guessing at. Where two candidates are that close, the more reliable
	 * one wins regardless of which comes first in the word.
	 */
	private static List<Integer> resolve(List<int[]> candidates, int gap) {
		candidates.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0]));
		List<Integer> kept = new ArrayList<>(candidates.size());
		for (int[] candidate : candidates) {
			boolean crowded = false;
			for (int accepted : kept) {
				if (Math.abs(candidate[0] - accepted) < gap) {
					crowded = true;
					break;
				}
			}
			if (!crowded) {
				kept.add(candidate[0]);
			}
		}
		kept.sort(Integer::compareTo);
		return kept;
	}

	private static boolean splitsDigraph(String lower, int cut) {
		if (cut <= 0 || cut >= lower.length()) {
			return false;
		}
		String pair = lower.substring(cut - 1, cut + 1);
		for (String digraph : EN_DIGRAPHS) {
			if (digraph.equals(pair)) {
				return true;
			}
		}
		return false;
	}

	// ---- scripts --------------------------------------------------------------------------------

	private enum Script { CYRILLIC, LATIN, MIXED }

	private static Script scriptOf(String word) {
		boolean cyrillic = false;
		boolean latin = false;
		for (int i = 0; i < word.length(); i++) {
			char c = Character.toLowerCase(word.charAt(i));
			if (c >= 'а' && c <= 'я' || c == 'ё') {
				cyrillic = true;
			} else if (c >= 'a' && c <= 'z') {
				latin = true;
			} else if (Character.isLetter(c)) {
				return Script.MIXED;
			}
		}
		if (cyrillic && latin) {
			return Script.MIXED;
		}
		return cyrillic ? Script.CYRILLIC : Script.LATIN;
	}

	// ---- TeX patterns, when the player supplies them --------------------------------------------

	private static Patterns patterns(String language) {
		if (LOADED.containsKey(language)) {
			return LOADED.get(language);
		}
		Patterns loaded = null;
		try {
			Path file = FabricLoader.getInstance().getConfigDir()
					.resolve(RoleplayersQuill.MOD_ID).resolve("hyphenation").resolve(language + ".pat");
			if (Files.isRegularFile(file)) {
				loaded = Patterns.read(Files.readAllLines(file, StandardCharsets.UTF_8));
				RoleplayersQuill.LOGGER.info("Read {} hyphenation patterns for {}", loaded.size(), language);
			}
		} catch (IOException | RuntimeException | LinkageError error) {
			// A missing file, an unreadable one, or no loader at all: the built-in rules are the
			// answer in every case, and none of them is worth an exception.
			RoleplayersQuill.LOGGER.debug("No hyphenation patterns for {}", language, error);
		}
		LOADED.put(language, loaded);
		return loaded;
	}

	/**
	 * Liang's algorithm, the one TeX uses: every pattern is a little piece of a word with a number
	 * between some of its letters, the patterns that match are laid over the word, the highest
	 * number at each position wins, and an odd number means a hyphen may go there.
	 */
	private static final class Patterns {
		private final Map<String, byte[]> byLetters = new HashMap<>();

		static Patterns read(List<String> lines) {
			Patterns patterns = new Patterns();
			for (String raw : lines) {
				String line = raw.trim();
				if (line.isEmpty() || line.startsWith("%") || line.startsWith("#")) {
					continue;
				}
				for (String pattern : line.split("\\s+")) {
					patterns.add(pattern.toLowerCase(Locale.ROOT));
				}
			}
			return patterns;
		}

		private void add(String pattern) {
			StringBuilder letters = new StringBuilder();
			List<Byte> values = new ArrayList<>();
			values.add((byte) 0);
			for (int i = 0; i < pattern.length(); i++) {
				char c = pattern.charAt(i);
				if (c >= '0' && c <= '9') {
					values.set(values.size() - 1, (byte) (c - '0'));
				} else {
					letters.append(c);
					values.add((byte) 0);
				}
			}
			byte[] array = new byte[values.size()];
			for (int i = 0; i < array.length; i++) {
				array[i] = values.get(i);
			}
			byLetters.put(letters.toString(), array);
		}

		int size() {
			return byLetters.size();
		}

		List<Integer> points(String word, int minBefore, int minAfter) {
			String padded = "." + word.toLowerCase(Locale.ROOT) + ".";
			int n = padded.length();
			byte[] best = new byte[n + 1];
			for (int start = 0; start < n; start++) {
				for (int end = start + 1; end <= n; end++) {
					byte[] values = byLetters.get(padded.substring(start, end));
					if (values == null) {
						continue;
					}
					for (int i = 0; i < values.length && start + i <= n; i++) {
						if (values[i] > best[start + i]) {
							best[start + i] = values[i];
						}
					}
				}
			}
			List<Integer> points = new ArrayList<>();
			// best[i] sits before padded.charAt(i); the leading dot shifts everything by one.
			for (int cut = minBefore; cut <= word.length() - minAfter; cut++) {
				if ((best[cut + 1] & 1) == 1) {
					points.add(cut);
				}
			}
			return points;
		}
	}
}
