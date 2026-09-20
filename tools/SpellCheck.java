import com.glamardor.roleplayersquill.text.Spelling;
import com.glamardor.roleplayersquill.text.WordList;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Checks the spelling machinery against the real word lists, without a game.
 *
 * <p>Three things can go quietly wrong here and none of them shows up in a screenshot: the Russian
 * list is Windows-1251 and is decoded by hand, the index is eight bytes a word and is read back
 * through a buffer, and the suggestions are generated rather than searched for. So the list is
 * fetched once into {@code build/dictionaries}, built, read back, and then asked about words whose
 * answers are known.
 *
 * <p>Run with: ./gradlew spellCheck
 */
public final class SpellCheck {
	private static int checks;
	private static int failures;

	public static void main(String[] args) throws Exception {
		Path dir = Path.of("build", "dictionaries");
		Files.createDirectories(dir);

		System.out.println();
		System.out.println("== two alphabets in one word");
		// A Latin C at the front of a Russian word: the same picture, and no dictionary anywhere
		// holds the result. The repair is the word with that letter put back where it belongs.
		expect(Spelling.mixedAlphabets("Cтановление"), "a Latin C in a Russian word went unnoticed");
		expect(Spelling.mixedAlphabets("рaбота"), "a Latin a in a Russian word went unnoticed");
		expect(!Spelling.mixedAlphabets("Становление"), "an ordinary Russian word was called mixed");
		expect(!Spelling.mixedAlphabets("parchment"), "an ordinary English word was called mixed");
		expect(!Spelling.mixedAlphabets("IT-отдел"), "a hyphenated pair of alphabets was called mixed");
		expect(Spelling.sameAlphabet("Cтановление").contains("Становление"),
				"the repair was not offered: " + Spelling.sameAlphabet("Cтановление"));
		expect(Spelling.sameAlphabet("рaбота").contains("работа"),
				"the repair was not offered: " + Spelling.sameAlphabet("рaбота"));
		System.out.println("  Cтановление → " + Spelling.sameAlphabet("Cтановление"));
		System.out.println("  рaбота → " + Spelling.sameAlphabet("рaбота"));
		// A letter with no twin in the other alphabet cannot be moved, and nothing is offered rather
		// than something invented: the word is still underlined, which is what was asked of it.
		System.out.println("  paбota → " + Spelling.sameAlphabet("paбota"));

		System.out.println();
		System.out.println("== the words that ride along in the jar");
		System.out.println("  " + Spelling.bundled().size() + " of them");
		expect(Spelling.bundled().size() > 200, "the bundled list did not load");
		for (String word : new String[] {"дварфы", "аш'каары", "квента", "люмуса", "хладорождённые"}) {
			expect(Spelling.bundled().contains(word), "\"" + word + "\" is not in the bundled list");
		}

		check(Spelling.Tongue.RUSSIAN, dir,
				new String[] {"книга", "книги", "книгами", "рассказывал", "пёс", "пес", "северный"},
				new String[] {"кнга", "рассказывалл", "бредлошадь"},
				new String[][] {
						{"кнга", "книга"},
						{"рассказывалл", "рассказывал"},
						{"привте", "привет"},
						{"какбы", "как бы"}});

		check(Spelling.Tongue.ENGLISH, dir,
				new String[] {"book", "books", "quill", "parchment"},
				new String[] {"bok", "qиill", "parchmentt"},
				new String[][] {
						{"bok", "book"},
						{"parchmentt", "parchment"}});

		System.out.println();
		System.out.println(checks + " checks, " + failures + " failed");
		if (failures > 0) {
			System.exit(1);
		}
	}

	private static void check(Spelling.Tongue tongue, Path dir, String[] known, String[] unknown,
			String[][] suggestions) throws Exception {
		System.out.println();
		System.out.println("== " + tongue.id());

		Path list = dir.resolve(tongue.id() + ".txt");
		if (!Files.isRegularFile(list)) {
			System.out.println("  fetching " + tongue.url());
			try (InputStream in = URI.create(tongue.url()).toURL().openStream()) {
				Files.copy(in, list, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		Path index = dir.resolve(tongue.id() + ".idx");

		long started = System.currentTimeMillis();
		WordList built = WordList.build(list, index, tongue.cyrillic());
		long building = System.currentTimeMillis() - started;

		started = System.currentTimeMillis();
		WordList read = WordList.read(index);
		long reading = System.currentTimeMillis() - started;

		expect(read != null, "the index could not be read back");
		expect(read != null && read.size() == built.size(),
				"built " + built.size() + " words and read back " + (read == null ? 0 : read.size()));
		System.out.println("  " + built.size() + " words, built in " + building + " ms, read in "
				+ reading + " ms, " + Files.size(index) / 1024 / 1024 + " MB on disk");

		for (String word : known) {
			expect(Spelling.knows(read, word), "\"" + word + "\" was not recognised");
		}
		for (String word : unknown) {
			expect(!Spelling.knows(read, word), "\"" + word + "\" was taken for a word");
		}
		for (String[] pair : suggestions) {
			List<String> offered = Spelling.suggestions(read, tongue.alphabet(), pair[0]);
			expect(offered.contains(pair[1]),
					"for \"" + pair[0] + "\" nothing offered \"" + pair[1] + "\": " + offered);
			System.out.println("  " + pair[0] + " → " + offered);
		}
	}

	private static void expect(boolean condition, String complaint) {
		checks++;
		if (!condition) {
			failures++;
			System.out.println("  FAILED: " + complaint);
		}
	}
}
