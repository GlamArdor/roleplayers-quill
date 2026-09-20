package com.glamardor.roleplayersquill.text;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.config.QuillConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Underlining the words that are not words, in a book full of names that are not words either.
 *
 * <p>Spelling on a roleplaying server is mostly a question of what <em>not</em> to underline. A
 * chapter of ordinary Russian carries three invented cities, two houses and a god, and a checker
 * that marks all six is a checker everybody turns off in the first minute. So three things keep it
 * quiet, in this order:
 *
 * <ul>
 *   <li>a word that begins with a capital is left alone, because that is what a name looks like and
 *       there is no dictionary in the world that holds the ones in this book. It is a setting, for
 *       whoever wants the opposite;
 *   <li>a word can be added to a dictionary of one's own – the same thing every word processor has
 *       under the same name – and it is a plain text file anybody can open and edit;
 *   <li>a word can be passed over until the book is closed, for the one that is spelled oddly on
 *       purpose.
 * </ul>
 *
 * <p>What is left is a straight membership test against a list of every form of every word, which is
 * how a language without a stemmer is checked. See {@link WordList} for why that costs twelve
 * megabytes rather than a hundred and fifty, and where the suggestions come from.
 */
public final class Spelling {

	/**
	 * A language, where its words come from, and what its letters are.
	 *
	 * <p>Neither list is shipped inside the mod. They are somebody else's work under somebody else's
	 * licence, they are bigger than everything in this jar put together, and most people writing on a
	 * Russian server will never want the English one. So they are fetched when they are asked for,
	 * the way the dictation models are.
	 */
	public enum Tongue {
		/**
		 * Russian: a million and a half forms, from Zaliznyak's tables by way of
		 * <a href="https://github.com/danakt/russian-words">danakt/russian-words</a>, MIT licensed
		 * and published in Windows-1251.
		 */
		RUSSIAN("ru", "https://raw.githubusercontent.com/danakt/russian-words/master/russian.txt",
				true, 18, "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"),
		/**
		 * English: three hundred and seventy thousand words from
		 * <a href="https://github.com/dwyl/english-words">dwyl/english-words</a>, plain ASCII.
		 */
		ENGLISH("en", "https://raw.githubusercontent.com/dwyl/english-words/master/words_alpha.txt",
				false, 2, "abcdefghijklmnopqrstuvwxyz");

		private final String id;
		private final String url;
		private final boolean cyrillic;
		private final int megabytes;
		private final String alphabet;

		Tongue(String id, String url, boolean cyrillic, int megabytes, String alphabet) {
			this.id = id;
			this.url = url;
			this.cyrillic = cyrillic;
			this.megabytes = megabytes;
			this.alphabet = alphabet;
		}

		public String id() {
			return id;
		}

		public String url() {
			return url;
		}

		public boolean cyrillic() {
			return cyrillic;
		}

		public int megabytes() {
			return megabytes;
		}

		public String alphabet() {
			return alphabet;
		}

		/** Whether this language is one the writer asked to be checked against. */
		public boolean wanted() {
			QuillConfig config = QuillConfig.get();
			return this == RUSSIAN ? config.spellRussian : config.spellEnglish;
		}

		public net.minecraft.text.Text label() {
			return net.minecraft.text.Text.translatable("roleplayersquill.spell.tongue." + id);
		}
	}

	/** Where a word sits in a paragraph, and what it says. */
	public record Word(int from, int to, String text) {
	}

	private static final EnumMap<Tongue, WordList> LOADED = new EnumMap<>(Tongue.class);
	private static final Set<Tongue> TRIED = java.util.EnumSet.noneOf(Tongue.class);
	private static volatile boolean loading;

	/** Words the writer has added, kept in a file they can open. */
	private static Set<String> personal;
	/** Words passed over for now: gone when the game is closed, which is what "for now" means. */
	private static final Set<String> IGNORED = new HashSet<>();

	/**
	 * The answer for a paragraph, kept until that paragraph changes.
	 *
	 * <p>The page is checked while it is being drawn, which is sixty times a second, and a page is a
	 * hundred and fifty words. The work is small either way; the rubbish is not.
	 */
	private static final Map<String, List<Word>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<Word>> eldest) {
			return size() > 96;
		}
	};

	private Spelling() {
	}

	// ---- where the lists live ---------------------------------------------------------------------

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID).resolve("dictionaries");
	}

	public static Path indexOf(Tongue tongue) {
		return directory().resolve(tongue.id() + ".idx");
	}

	/** Where the downloaded list is put while it is being turned into an index. */
	public static Path listOf(Tongue tongue) {
		return directory().resolve(tongue.id() + ".txt");
	}

	public static boolean isInstalled(Tongue tongue) {
		return Files.isRegularFile(indexOf(tongue));
	}

	/** Whether anything at all can be checked, which is what the toolbar button needs to know. */
	public static boolean anyInstalled() {
		for (Tongue tongue : Tongue.values()) {
			if (tongue.wanted() && isInstalled(tongue)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a language is wanted and not yet here, which is what would have to be fetched. */
	public static List<Tongue> missing() {
		List<Tongue> out = new ArrayList<>();
		for (Tongue tongue : Tongue.values()) {
			if (tongue.wanted() && !isInstalled(tongue)) {
				out.add(tongue);
			}
		}
		return out;
	}

	// ---- reading them in --------------------------------------------------------------------------

	/**
	 * Reads whatever is installed, once, on a thread of its own.
	 *
	 * <p>Twelve megabytes off a disk is a tenth of a second, and a tenth of a second on the thread
	 * that draws is a stutter at exactly the moment a book opens. Until it is in, nothing is
	 * underlined – which is right, because "not known to be wrong" is the honest answer then.
	 */
	public static void load() {
		if (loading) {
			return;
		}
		boolean anything = false;
		for (Tongue tongue : Tongue.values()) {
			if (tongue.wanted() && !TRIED.contains(tongue) && isInstalled(tongue)) {
				anything = true;
			}
		}
		if (!anything) {
			return;
		}
		loading = true;
		Thread thread = new Thread(() -> {
			try {
				for (Tongue tongue : Tongue.values()) {
					if (!tongue.wanted() || TRIED.contains(tongue) || !isInstalled(tongue)) {
						continue;
					}
					try {
						WordList list = WordList.read(indexOf(tongue));
						synchronized (LOADED) {
							TRIED.add(tongue);
							if (list != null) {
								LOADED.put(tongue, list);
							}
						}
						if (list == null) {
							RoleplayersQuill.LOGGER.warn("The {} dictionary index could not be read", tongue.id());
						} else {
							RoleplayersQuill.LOGGER.info("Spelling: {} words of {}", list.size(), tongue.id());
						}
					} catch (IOException error) {
						RoleplayersQuill.LOGGER.warn("Could not read the {} dictionary", tongue.id(), error);
						synchronized (LOADED) {
							TRIED.add(tongue);
						}
					}
				}
				forget();
			} finally {
				loading = false;
			}
		}, "roleplayers-quill-dictionary");
		thread.setDaemon(true);
		thread.start();
	}

	/** Forgets a language, so that a freshly built index is read instead of the old answer. */
	public static void reload(Tongue tongue) {
		synchronized (LOADED) {
			TRIED.remove(tongue);
			LOADED.remove(tongue);
		}
		forget();
		load();
	}

	private static WordList listFor(Tongue tongue) {
		synchronized (LOADED) {
			return LOADED.get(tongue);
		}
	}

	/** Whether anything has actually been read in yet. */
	public static boolean ready() {
		synchronized (LOADED) {
			return !LOADED.isEmpty();
		}
	}

	// ---- checking ----------------------------------------------------------------------------------

	/** Throws away what was worked out before, because the answer would be different now. */
	public static void forget() {
		synchronized (CACHE) {
			CACHE.clear();
		}
	}

	/**
	 * Every word in this paragraph that nothing here recognises.
	 *
	 * <p>Empty while the check is off, while nothing is loaded, and for a paragraph of names – all
	 * three of which are the same answer as far as the page is concerned: draw nothing.
	 */
	public static List<Word> unknownIn(Paragraph paragraph) {
		if (!QuillConfig.get().spellCheck || !ready()) {
			return List.of();
		}
		String text = paragraph.text();
		if (text.isBlank()) {
			return List.of();
		}
		synchronized (CACHE) {
			List<Word> known = CACHE.get(text);
			if (known != null) {
				return known;
			}
		}
		List<Word> found = scan(text);
		synchronized (CACHE) {
			CACHE.put(text, found);
		}
		return found;
	}

	private static List<Word> scan(String text) {
		List<Word> out = new ArrayList<>();
		int i = 0;
		while (i < text.length()) {
			if (!Character.isLetter(text.charAt(i))) {
				i++;
				continue;
			}
			int start = i;
			int end = i;
			while (end < text.length()) {
				char c = text.charAt(end);
				if (Character.isLetter(c)) {
					end++;
					continue;
				}
				// A hyphen or an apostrophe belongs to the word only with letters on both sides of it.
				if ((c == '-' || c == '\'' || c == '’') && end + 1 < text.length()
						&& Character.isLetter(text.charAt(end + 1))) {
					end++;
					continue;
				}
				break;
			}
			String word = text.substring(start, end);
			if (!recognised(word)) {
				out.add(new Word(start, end, word));
			}
			i = end;
		}
		return out.isEmpty() ? List.of() : out;
	}

	/**
	 * Whether a word is one this has any business objecting to.
	 *
	 * <p>Most of the answers here are "leave it alone" rather than "it is in the list", and that is
	 * deliberate: everything a book of roleplay is full of – names, coined words, numbers written
	 * into words, initials – is a thing no dictionary can hold and no writer wants underlined.
	 */
	public static boolean recognised(String word) {
		if (word.length() < 3) {
			return true;
		}
		for (int i = 0; i < word.length(); i++) {
			if (Character.isDigit(word.charAt(i))) {
				return true;
			}
		}
		String lower = word.toLowerCase(Locale.ROOT).replace('’', '\'');
		if (personal().contains(lower) || bundled().contains(lower) || IGNORED.contains(lower)) {
			return true;
		}
		// Two alphabets in one word is a mistake whatever else it is – a name, a capital, an
		// abbreviation – so it is caught before any of the rules that let those through.
		if (mixedAlphabets(word)) {
			return false;
		}
		Tongue tongue = tongueOf(word);
		if (tongue == null || !tongue.wanted()) {
			return true;
		}
		WordList list = listFor(tongue);
		if (list == null) {
			return true;
		}
		if (isShouted(word)) {
			// Written in capitals: an abbreviation, or a heading, and neither is a spelling question.
			return true;
		}
		if (QuillConfig.get().spellSkipCapitals && Character.isUpperCase(word.charAt(0))) {
			return true;
		}
		return knows(list, lower);
	}

	/**
	 * A word written in two alphabets at once, which is nobody's word and everybody's mistake.
	 *
	 * <p>Russian and Latin share a dozen letters that are the same picture – С and C, о and o, р and
	 * p – so a word can come back from a copy, a keyboard swapped mid-word or a paste from a browser
	 * with one letter belonging to the wrong alphabet. Nothing shows: it reads exactly as it should
	 * and no dictionary holds it, which is why a spelling check that skips what it cannot place in a
	 * language – the rule until now – walked straight past it.
	 *
	 * <p>Asked of each half of a hyphenated word separately, because "IT-отдел" and "Wi-Fi-роутер"
	 * are two alphabets in one word on purpose.
	 */
	public static boolean mixedAlphabets(String word) {
		for (String part : word.split("[-'’]")) {
			boolean cyrillic = false;
			boolean latin = false;
			for (int i = 0; i < part.length(); i++) {
				char c = part.charAt(i);
				if (!Character.isLetter(c)) {
					continue;
				}
				if (c >= 0x400 && c <= 0x4FF) {
					cyrillic = true;
				} else if (c < 0x80) {
					latin = true;
				}
			}
			if (cyrillic && latin) {
				return true;
			}
		}
		return false;
	}

	/** Whether the list holds this word, with the two ways of writing ё counted as one. */
	public static boolean knows(WordList list, String lower) {
		if (list.knows(lower) || bundled().contains(lower)) {
			return true;
		}
		if (lower.indexOf('ё') >= 0 && list.knows(lower.replace('ё', 'е'))) {
			return true;
		}
		// A compound the list does not carry whole, like a made-up "боец-отступник", is spelled
		// correctly if both halves are. Only where both halves are words in their own right.
		int hyphen = lower.indexOf('-');
		if (hyphen <= 0 || hyphen >= lower.length() - 1) {
			return false;
		}
		for (String part : lower.split("-")) {
			if (part.length() < 2) {
				continue;
			}
			if (!list.knows(part) && !list.knows(part.replace('ё', 'е'))) {
				return false;
			}
		}
		return true;
	}

	/** Which language's letters this word is written in, or null when it is a mixture or neither. */
	public static Tongue tongueOf(String word) {
		Tongue tongue = null;
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			if (!Character.isLetter(c)) {
				continue;
			}
			Tongue here = c >= 0x400 && c <= 0x4FF ? Tongue.RUSSIAN
					: c < 0x80 ? Tongue.ENGLISH
					: null;
			if (here == null || tongue != null && tongue != here) {
				return null;
			}
			tongue = here;
		}
		return tongue;
	}

	private static boolean isShouted(String word) {
		boolean anyLetter = false;
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			if (!Character.isLetter(c)) {
				continue;
			}
			anyLetter = true;
			if (!Character.isUpperCase(c)) {
				return false;
			}
		}
		return anyLetter;
	}

	// ---- suggestions -------------------------------------------------------------------------------

	/**
	 * What was probably meant: every word one keystroke away from this one that the list knows.
	 *
	 * <p>The candidates are made rather than searched for – a letter dropped, two swapped, one
	 * changed, one added, and the word split in two – and each is then asked about. That is why the
	 * dictionary can be kept as hashes with the words themselves thrown away: nothing here ever needs
	 * to read a word out of it, only to ask whether a word it already holds is in there.
	 *
	 * <p>A dozen letters and thirty-three of them in the alphabet comes to under a thousand
	 * questions, and a question is a binary search. It is done when the menu opens, not while typing.
	 */
	public static List<String> suggest(String word) {
		if (mixedAlphabets(word)) {
			// The whole answer is one of two words, and both are this one with its letters put into
			// one alphabet. Worth asking about before anything else: a word spelled correctly in two
			// alphabets has no near neighbours at all, so the ordinary search would come back empty.
			List<String> out = new ArrayList<>();
			for (String repair : sameAlphabet(word)) {
				Tongue tongue = tongueOf(repair);
				WordList list = tongue == null ? null : listFor(tongue);
				if (list != null && knows(list, repair.toLowerCase(Locale.ROOT))) {
					out.add(repair);
				}
			}
			return out;
		}
		Tongue tongue = tongueOf(word);
		if (tongue == null) {
			return List.of();
		}
		WordList list = listFor(tongue);
		if (list == null) {
			return List.of();
		}
		return suggestions(list, tongue.alphabet(), word);
	}

	/**
	 * The same word written in one alphabet: all Russian, and all Latin.
	 *
	 * <p>Only the letters that are the same picture in both are moved. A Latin "c" in a Russian word
	 * is the Russian "с" somebody meant; a Latin "q" is not anything, and a word holding one is not a
	 * word that a change of alphabet will mend, so it is left as it is and offered as nothing.
	 */
	public static List<String> sameAlphabet(String word) {
		List<String> out = new ArrayList<>();
		String cyrillic = translated(word, LATIN, CYRILLIC);
		if (cyrillic != null && !cyrillic.equals(word)) {
			out.add(cyrillic);
		}
		String latin = translated(word, CYRILLIC, LATIN);
		if (latin != null && !latin.equals(word)) {
			out.add(latin);
		}
		return out;
	}

	/** The letters the two alphabets draw identically, each opposite its twin. */
	private static final String LATIN = "ABCEHKMOPTXYaceopxyk";
	private static final String CYRILLIC = "АВСЕНКМОРТХУасеорхук";

	/**
	 * Every letter of one alphabet turned into its twin in the other, the rest left as it stands.
	 *
	 * @return null when a letter being moved has no twin, so the word cannot be written that way
	 */
	private static String translated(String word, String from, String to) {
		boolean fromLatin = from.equals(LATIN);
		StringBuilder out = new StringBuilder(word.length());
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			boolean moving = Character.isLetter(c)
					&& (fromLatin ? c < 0x80 : c >= 0x400 && c <= 0x4FF);
			if (!moving) {
				out.append(c);
				continue;
			}
			int at = from.indexOf(c);
			if (at < 0) {
				return null;
			}
			out.append(to.charAt(at));
		}
		return out.toString();
	}

	/**
	 * The same, against a list handed in rather than one that has been loaded.
	 *
	 * <p>Pure arithmetic over a word and an alphabet, which is what makes it checkable without a
	 * game: see {@code tools/SpellCheck.java}.
	 */
	public static List<String> suggestions(WordList list, String alphabet, String word) {
		String lower = word.toLowerCase(Locale.ROOT);
		Map<String, Integer> found = new LinkedHashMap<>();

		// Two letters the wrong way round: the slip whose answer is nearly always the word meant.
		for (int i = 0; i + 1 < lower.length(); i++) {
			take(list, found, lower.substring(0, i) + lower.charAt(i + 1) + lower.charAt(i)
					+ lower.substring(i + 2), 0, lower);
		}
		// A letter missed out, then one typed twice: both are one keystroke, and both keep every
		// letter that was typed, which is why they beat a letter guessed wrong.
		for (int i = 0; i <= lower.length(); i++) {
			for (int a = 0; a < alphabet.length(); a++) {
				take(list, found, lower.substring(0, i) + alphabet.charAt(a) + lower.substring(i), 1, lower);
			}
		}
		for (int i = 0; i < lower.length(); i++) {
			take(list, found, lower.substring(0, i) + lower.substring(i + 1), 1, lower);
		}
		// Two words with the space left out, which is what a fast hand does to "как бы". Last of all
		// the kinds, because a list of a million and a half forms will cut almost any long word into
		// two things that are technically words, and one keystroke wrong is the likelier story.
		for (int i = 2; i + 2 <= lower.length(); i++) {
			String left = lower.substring(0, i);
			String right = lower.substring(i);
			if (knows(list, left) && knows(list, right)) {
				found.putIfAbsent(left + " " + right, 5);
			}
		}
		for (int i = 0; i < lower.length(); i++) {
			for (int a = 0; a < alphabet.length(); a++) {
				char c = alphabet.charAt(a);
				if (c != lower.charAt(i)) {
					take(list, found, lower.substring(0, i) + c + lower.substring(i + 1), 3, lower);
				}
			}
		}

		// The first letter is the one nobody gets wrong, so a suggestion that changes it is a
		// suggestion about a different word and goes to the back whatever kind of slip it is.
		List<Map.Entry<String, Integer>> ranked = new ArrayList<>(found.entrySet());
		ranked.sort(java.util.Comparator.comparingInt(Map.Entry::getValue));

		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : ranked) {
			String suggestion = entry.getKey();
			out.add(Character.isUpperCase(word.charAt(0))
					? Character.toUpperCase(suggestion.charAt(0)) + suggestion.substring(1)
					: suggestion);
			if (out.size() >= 7) {
				break;
			}
		}
		return out;
	}

	private static void take(WordList list, Map<String, Integer> found, String candidate, int rank,
			String original) {
		if (found.size() >= 64 || candidate.length() < 3 || !knows(list, candidate)) {
			return;
		}
		int cost = rank + (candidate.charAt(0) == original.charAt(0) ? 0 : 4);
		found.merge(candidate, cost, Math::min);
	}

	// ---- the writer's own words ----------------------------------------------------------------------

	/**
	 * The words that ride along inside the mod: fantasy, and one server's setting.
	 *
	 * <p>No list of Russian holds "дварфы", "аш'каары" or "квента", and every roleplayer writes all
	 * three. They are a few hundred lines in the jar rather than a download, and they cost nothing to
	 * anybody who never opens a book about elves: an underline that is not drawn.
	 *
	 * <p>Read once, lazily, and kept. Names beginning with a capital are passed over anyway unless
	 * that setting is turned off, so most of what this buys is the lower-case forms.
	 */
	public static Set<String> bundled() {
		if (bundledWords != null) {
			return bundledWords;
		}
		Set<String> words = new HashSet<>();
		try (java.io.InputStream in = Spelling.class.getResourceAsStream(BUNDLED)) {
			if (in != null) {
				try (java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
					String line;
					while ((line = reader.readLine()) != null) {
						String word = line.strip().toLowerCase(Locale.ROOT);
						if (!word.isEmpty() && !word.startsWith("#")) {
							words.add(word);
						}
					}
				}
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not read {}", BUNDLED, error);
		}
		bundledWords = words;
		return bundledWords;
	}

	private static final String BUNDLED = "/assets/roleplayersquill/spelling/extra.txt";
	private static Set<String> bundledWords;

	private static Path personalFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID)
				.resolve("dictionary.txt");
	}

	/**
	 * The words added by hand, read from a file anybody can open.
	 *
	 * <p>A plain list, one word to a line, in the mod's config folder. It is meant to be edited: a
	 * server with fifty place names is fifty lines pasted in once, rather than fifty right-clicks.
	 */
	public static Set<String> personal() {
		if (personal != null) {
			return personal;
		}
		Set<String> words = new HashSet<>();
		Path file = personalFile();
		if (Files.isRegularFile(file)) {
			try {
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					String word = line.strip().toLowerCase(Locale.ROOT);
					if (!word.isEmpty() && !word.startsWith("#")) {
						words.add(word);
					}
				}
			} catch (IOException error) {
				RoleplayersQuill.LOGGER.warn("Could not read {}", file, error);
			}
		}
		personal = words;
		return personal;
	}

	/** Adds a word to the writer's own dictionary, for good. */
	public static void learn(String word) {
		String lower = word.toLowerCase(Locale.ROOT).replace('’', '\'');
		if (lower.isBlank() || !personal().add(lower)) {
			return;
		}
		Path file = personalFile();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, lower + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not add {} to {}", lower, file, error);
		}
		forget();
	}

	/** Passes a word over until the game is closed. */
	public static void ignore(String word) {
		IGNORED.add(word.toLowerCase(Locale.ROOT).replace('’', '\''));
		forget();
	}
}
