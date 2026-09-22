package com.glamardor.roleplayersquill.book;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.text.Alignment;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.ListStyle;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import com.glamardor.roleplayersquill.text.QuillStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Books on disk: exported, imported, and quietly kept alongside the ones being written.
 *
 * <p>Two file formats, because they are for two different things. A {@code .txt} is for a person –
 * it opens in any editor, it can be handed to someone else, and it keeps the {@code §} codes so it
 * can be pasted back into a book on a server where this mod is not installed. A {@code .json} is
 * for the mod – it keeps the paragraphs, the alignment, the links and the colour of every single
 * character, so that a book exported and imported again is the same book.
 *
 * <h2>Drafts</h2>
 *
 * <p>A book on a server is a list of strings and nothing else: there is nowhere in it to record
 * that the third paragraph is centred or that a word is a link. So the document is also kept
 * locally, filed under a fingerprint of the pages themselves, and when a book is opened whose pages
 * match a draft, the draft is what opens. Nothing is sent anywhere and nothing is required of the
 * server; a book edited on another machine simply comes back as plain text, which is what it is.
 */
public final class BookIO {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FORMAT = 1;
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

	private BookIO() {
	}

	// ---- where things live ----------------------------------------------------------------------

	public static Path exportDir() {
		Path dir = FabricLoader.getInstance().getGameDir().resolve(QuillConfig.get().exportFolder);
		try {
			Files.createDirectories(dir);
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not make {}", dir, error);
		}
		return dir;
	}

	private static Path draftDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID).resolve("drafts");
	}

	private static Path templateDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID).resolve("templates");
	}

	/**
	 * The shelf: every book that has been read here, kept whole.
	 *
	 * <p>Apart from the drafts on purpose. A draft is a convenience and is thrown away once there are
	 * a hundred and fifty of them; a book somebody lent to be read, or one's own book now lying in a
	 * lava pool with the corpse that carried it, is the thing being kept – and an evening of writing
	 * drafts should not quietly push last month's library off the end.
	 */
	private static Path libraryDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID).resolve("library");
	}

	/** Sets of formatting live beside the templates, in a folder of their own. */
	private static Path setDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID).resolve("sets");
	}

	/**
	 * A few paragraphs kept under a name, to be dropped into whatever is being written.
	 *
	 * <p>Kept exactly the way a template is – as a document with one page in it – so that a set
	 * carries its alignment, its colours and its links along with its words.
	 */
	public static void saveSet(String name, List<Paragraph> paragraphs) {
		QuillDocument document = new QuillDocument();
		document.pages().clear();
		document.pages().add(QuillDocument.copyPage(paragraphs));
		try {
			writeDocument(document, ensure(setDir()).resolve(fileNameOf(name) + ".json"));
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not keep the set {}", name, error);
		}
	}

	/** Every set kept this way, by name. */
	public static java.util.LinkedHashMap<String, List<Paragraph>> sets() {
		return readPages(setDir());
	}

	public static void deleteSet(String name) {
		try {
			Files.deleteIfExists(setDir().resolve(fileNameOf(name) + ".json"));
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not remove the set {}", name, error);
		}
	}

	private static java.util.LinkedHashMap<String, List<Paragraph>> readPages(Path dir) {
		java.util.LinkedHashMap<String, List<Paragraph>> out = new java.util.LinkedHashMap<>();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (var stream = Files.list(dir)) {
			List<Path> files = new ArrayList<>(stream.filter(Files::isRegularFile).toList());
			files.sort(java.util.Comparator.comparing(Path::getFileName));
			for (Path file : files) {
				QuillDocument document = readDocument(file);
				if (document != null && !document.pages().isEmpty()) {
					out.put(file.getFileName().toString().replaceFirst("\\.json$", ""), document.page(0));
				}
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not read {}", dir, error);
		}
		return out;
	}

	/**
	 * A page kept to start other pages from, under a name of its own.
	 *
	 * <p>Saved as a whole document with one page in it, which is the same thing a draft is, so the
	 * reading and writing are already written and a template is whatever a page was: alignment,
	 * colours, links and all.
	 */
	public static void saveTemplate(String name, List<Paragraph> page) {
		QuillDocument document = new QuillDocument();
		document.pages().clear();
		document.pages().add(QuillDocument.copyPage(page));
		Path dir = ensure(templateDir());
		try {
			writeDocument(document, dir.resolve(fileNameOf(name) + ".json"));
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not keep the template {}", name, error);
		}
	}

	/** Every page kept this way, by name. */
	public static java.util.LinkedHashMap<String, List<Paragraph>> templates() {
		java.util.LinkedHashMap<String, List<Paragraph>> out = new java.util.LinkedHashMap<>();
		Path dir = templateDir();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (var stream = Files.list(dir)) {
			List<Path> files = new ArrayList<>(stream.filter(Files::isRegularFile).toList());
			files.sort(java.util.Comparator.comparing(Path::getFileName));
			for (Path file : files) {
				QuillDocument document = readDocument(file);
				if (document != null && !document.pages().isEmpty()) {
					String name = file.getFileName().toString().replaceFirst("\\.json$", "");
					out.put(name, document.page(0));
				}
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not read the templates", error);
		}
		return out;
	}

	public static void deleteTemplate(String name) {
		try {
			Files.deleteIfExists(templateDir().resolve(fileNameOf(name) + ".json"));
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not remove the template {}", name, error);
		}
	}

	/** The name as a file can hold it, which is the name the list shows back. */
	private static String fileNameOf(String name) {
		StringBuilder safe = new StringBuilder();
		for (char c : name.toCharArray()) {
			safe.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ' ? c : '_');
		}
		String trimmed = safe.toString().strip();
		return trimmed.isEmpty() ? "template" : trimmed;
	}

	private static Path ensure(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not make {}", dir, error);
		}
		return dir;
	}

	/** A filename that will not collide and will not be rejected by the file system. */
	public static String suggestName(String title, String extension) {
		String base = title == null || title.isBlank() ? "book" : title;
		StringBuilder safe = new StringBuilder();
		for (char c : LegacyCodec.strip(base).toCharArray()) {
			safe.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ' ? c : '_');
		}
		String trimmed = safe.toString().strip();
		if (trimmed.isEmpty()) {
			trimmed = "book";
		}
		return trimmed + "-" + LocalDateTime.now().format(STAMP) + "." + extension;
	}

	/**
	 * The name somebody typed for a book they are exporting, as a file can hold it.
	 *
	 * <p>Nothing is added to it – no date, no title – because a name that was typed is a name that
	 * was meant. The dated {@link #suggestName} is what happens when nothing was typed, which is the
	 * case where a name has to be invented and had better not collide with the last invented one.
	 */
	public static String fileNameFor(String typed, String fallbackTitle, String extension) {
		String trimmed = typed == null ? "" : LegacyCodec.strip(typed).strip();
		if (trimmed.isEmpty()) {
			return suggestName(fallbackTitle, extension);
		}
		StringBuilder safe = new StringBuilder();
		for (char c : trimmed.toCharArray()) {
			safe.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ' ? c : '_');
		}
		String name = safe.toString().strip();
		return (name.isEmpty() ? "book" : name) + "." + extension;
	}

	// ---- text -------------------------------------------------------------------------------------

	/**
	 * @param keepCodes  leave the {@code §} codes in, so the file can be pasted straight back
	 * @param separator  the line written between pages; blank for none
	 */
	public static Path exportText(QuillDocument document, List<String> encodedPages, boolean keepCodes,
			String separator) throws IOException {
		StringBuilder out = new StringBuilder();
		if (!document.title().isBlank()) {
			out.append(keepCodes ? document.title() : LegacyCodec.strip(document.title())).append('\n');
			if (!separator.isBlank()) {
				out.append(separator).append('\n');
			}
		}
		for (int i = 0; i < encodedPages.size(); i++) {
			if (i > 0 && !separator.isBlank()) {
				out.append('\n').append(separator).append('\n');
			}
			String page = encodedPages.get(i);
			out.append(keepCodes ? page : LegacyCodec.strip(page));
			out.append('\n');
		}
		Path file = exportDir().resolve(suggestName(document.title(), "txt"));
		Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
		return file;
	}

	public static String readText(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	// ---- the mod's own format ---------------------------------------------------------------------

	public static Path exportDocument(QuillDocument document) throws IOException {
		Path file = exportDir().resolve(suggestName(document.title(), "json"));
		writeDocument(document, file);
		return file;
	}

	public static void writeDocument(QuillDocument document, Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(toDto(document), writer);
		}
	}

	@Nullable
	public static QuillDocument readDocument(Path file) {
		DocumentDto dto = readDocumentDto(file);
		return dto == null ? null : fromDto(dto);
	}

	@Nullable
	private static DocumentDto readDocumentDto(Path file) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, DocumentDto.class);
		} catch (IOException | RuntimeException error) {
			RoleplayersQuill.LOGGER.warn("Could not read {}", file, error);
			return null;
		}
	}

	public static boolean looksLikeDocument(Path file) {
		return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
	}

	/**
	 * Writes down the exact strings the book was just sent as.
	 *
	 * <p>For answering "the editor and the book do not match" without guessing. A screenshot shows
	 * what a page looks like; this shows what it <em>is</em>, measured with the font the game is
	 * actually running rather than a stand-in – which is the difference that matters, because every
	 * pad space in a page is counted against the real glyph widths.
	 *
	 * <p>Section signs are written as ampersands so the file can be read, and every line is numbered
	 * and fenced so trailing spaces are visible.
	 */
	public static void writeLastSave(List<String> pages) {
		StringBuilder out = new StringBuilder();
		out.append("Written ").append(LocalDateTime.now()).append('\n');
		for (int p = 0; p < pages.size(); p++) {
			String page = pages.get(p);
			String[] lines = page.split("\n", -1);
			out.append("\n### page ").append(p + 1).append(": ").append(lines.length)
					.append(" lines, ").append(page.length()).append(" characters\n");
			for (int i = 0; i < lines.length; i++) {
				out.append(String.format("%3d |%s|%n", i + 1,
						lines[i].replace(LegacyCodec.SECTION, '&')));
			}
		}
		Path file = FabricLoader.getInstance().getConfigDir()
				.resolve(RoleplayersQuill.MOD_ID).resolve("last-save.txt");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not write {}", file, error);
		}
	}

	// ---- drafts -----------------------------------------------------------------------------------

	/**
	 * A fingerprint of the pages as the server holds them, and of whose book it is.
	 *
	 * <h2>Why the owner is in here</h2>
	 *
	 * <p>Because the pages on their own do not tell two books apart, and every blank book in the world
	 * has the same pages. A draft is filed under the book as it was opened, so once drafts began to be
	 * written while a book was still being typed – rather than only when it was sent – every fresh
	 * book shared one file, and the next blank book picked up opened as whatever the last one had
	 * become. Fifty pages of somebody else's work in a book straight off the crafting table.
	 *
	 * <p>So for a book with nothing in it the key also carries who is holding it and where: that is
	 * all the identity a blank book has. Once a book has any text at all the text is identity enough,
	 * and the owner is left out – otherwise the same book would be filed under a different name after
	 * being moved from one hand to the other.
	 */
	public static String keyOf(List<String> pages, String owner) {
		// A blank book has no text to be known by, so its draft is filed under whose it is and
		// nothing else: one piece of work in hand per player, which is all anybody has at a time.
		// Deliberately not the slot as well – a book carried from one slot to another is the same
		// book, and a draft that could not survive being moved would be no use at all.
		if (isBlank(pages)) {
			return "blank-" + owner.replaceAll("[^A-Za-z0-9-]", "");
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			for (String page : pages) {
				digest.update(page.getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}

			StringBuilder hex = new StringBuilder();
			for (byte b : digest.digest()) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException error) {
			return Integer.toHexString(String.join(" ", pages).hashCode());
		}
	}

	/** Whether a book has nothing written in it at all, which is when it has no identity of its own. */
	public static boolean isBlank(List<String> pages) {
		for (String page : pages) {
			if (!LegacyCodec.strip(page).isBlank()) {
				return false;
			}
		}
		return true;
	}

	public static void saveDraft(QuillDocument document, List<String> encodedPages, String owner) {
		String key = keyOf(encodedPages, owner);
		Path file = draftDir().resolve(key + ".json");
		try {
			writeDocument(document, file);
			rememberPages(document.id(), key, encodedPages, false);
			pruneDrafts();
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not keep the draft at {}", file, error);
		}
	}

	/**
	 * Keeps a copy of a signed book being read, so it turns up in a search across every book on
	 * this computer, the same way anything written here does.
	 *
	 * <p>Filed apart from an ordinary draft in one respect: it is marked as somebody else's book
	 * rather than a piece of work in hand, so that writing a new book which happens to say the same
	 * thing – the same form letter, typed again – never quietly opens with this one's title and
	 * history sitting on it. {@link #loadDraft} and {@link #reopen} both skip anything marked this
	 * way; {@link #allBooks} does not, since the entire point of keeping it is to be found there.
	 *
	 * @return whether it was kept, so the reader can say so rather than leave a button that looks
	 *         like it did nothing
	 */
	public static boolean keepSigned(QuillDocument document, List<String> encodedPages) {
		if (isBlank(encodedPages)) {
			return false;
		}
		String key = keyOf(encodedPages, "signed");
		Path file = libraryDir().resolve(key + ".json");
		try {
			Files.createDirectories(file.getParent());
			DocumentDto dto = toDto(document);
			dto.signed = true;
			// Named after what is written in it rather than after the moment it was read, so that the
			// same book read twice is the same book on the shelf. A signed book cannot change; a
			// second reading of it that arrived under a fresh random name would be a second copy in
			// every list, and the list is meant to be a shelf rather than a log of what was opened.
			dto.id = "signed-" + key;
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(dto, writer);
			}
			rememberPages(dto.id, key, encodedPages, true);
			pruneLibrary();
			return true;
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not keep the book at {}", file, error);
			return false;
		}
	}

	// ---- the books somebody said were the ones that matter ----------------------------------------

	/**
	 * Which books are starred, kept beside them rather than in them.
	 *
	 * <p>In a file of its own on purpose. A star is about the reader, not about the book: writing it
	 * into the book's own file would mean rewriting the book to press a star, which changes when the
	 * book was last written, and a shelf ordered by that would reshuffle itself under the hand that
	 * touched it.
	 *
	 * <p>Books are starred by the name they go by between sessions, so a starred book that is
	 * written back, read again or filed afresh is the same starred book. See {@link QuillDocument#id}.
	 */
	private static java.util.Set<String> cachedFavourites;

	public static java.util.Set<String> favourites() {
		if (cachedFavourites != null) {
			return cachedFavourites;
		}
		java.util.Set<String> starred = new java.util.LinkedHashSet<>();
		Path file = favouritesFile();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				FavouritesDto dto = GSON.fromJson(reader, FavouritesDto.class);
				if (dto != null && dto.books != null) {
					for (String id : dto.books) {
						if (id != null && !id.isBlank()) {
							starred.add(id);
						}
					}
				}
			} catch (IOException | RuntimeException error) {
				RoleplayersQuill.LOGGER.debug("Could not read {}", file, error);
			}
		}
		cachedFavourites = starred;
		return cachedFavourites;
	}

	public static boolean isFavourite(String id) {
		return favourites().contains(id);
	}

	/** Stars a book or unstars it, and writes it down at once: this is one line in a small file. */
	public static void setFavourite(String id, boolean starred) {
		java.util.Set<String> all = favourites();
		if (starred ? !all.add(id) : !all.remove(id)) {
			return;
		}
		FavouritesDto dto = new FavouritesDto();
		dto.format = FORMAT;
		dto.books = new ArrayList<>(all);
		Path file = favouritesFile();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(dto, writer);
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not write {}", file, error);
		}
	}

	private static Path favouritesFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID)
				.resolve("favourites.json");
	}

	static final class FavouritesDto {
		int format;
		List<String> books;
	}

	/**
	 * Takes a book off the shelf for good, every file of it.
	 *
	 * <p>Every file, because one row is one book: leaving the second copy behind would only mean the
	 * book came back tomorrow under its own line, which is not what anybody pressing this meant.
	 *
	 * <p>A starred book is never removed here. That is not this method being careful on the caller's
	 * behalf – the shelf says so before it ever calls – it is the one rule about starred books being
	 * kept in the one place that can keep it.
	 */
	public static boolean forget(Kept book) {
		if (isFavourite(book.document().id())) {
			return false;
		}
		boolean done = true;
		for (Path file : book.copies()) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException error) {
				RoleplayersQuill.LOGGER.warn("Could not remove {}", file, error);
				done = false;
			}
		}
		return done;
	}

	@Nullable
	public static QuillDocument loadDraft(List<String> pages, String owner) {
		Path file = draftDir().resolve(keyOf(pages, owner) + ".json");
		if (!Files.isRegularFile(file)) {
			return null;
		}
		DocumentDto dto = readDocumentDto(file);
		if (dto == null || dto.signed) {
			return null;
		}
		QuillDocument document = fromDto(dto);
		// Never for a blank book. Two blank books are the same book as far as anything here can
		// tell, so opening one with the last one's text in it is a guess, and the guess was wrong
		// often enough to hand people fifty pages of somebody else's work. What happens instead is
		// that the editor offers it – see blankDraft.
		if (isBlank(pages)) {
			return null;
		}
		// Drafts written before the mod could read a list back hold their bullets as plain text, and a
		// draft is loaded in preference to the page – so without this the book would go on forgetting
		// that its list is a list for as long as the draft lasted.
		for (List<Paragraph> page : document.pages()) {
			LegacyCodec.restoreLists(page);
		}
		return document;
	}

	// ---- the same book after somebody else has changed it -------------------------------------------

	/**
	 * A draft is filed under the exact pages the server holds, and that is right until the server's
	 * copy changes without this client doing it.
	 *
	 * <p>Which happens: the server's torn-page plugin takes a page out of the book while it is in
	 * somebody's hand. The pages are now different, the fingerprint is different, the draft is not
	 * found, and the book comes back as a stranger – it forgets its links and its colours, and, worse,
	 * it forgets its name, so the versions kept under that name are no longer its versions. "No saves
	 * yet" about a book saved all evening.
	 *
	 * <p>So a second way of recognising a book, used only when the first fails: the pages it still
	 * has in common with a draft. Twenty-five pages of twenty-six is the same book by any reasonable
	 * test, and a page is compared by what is written on it with the spacing taken out, so a page
	 * written again slightly differently still counts as itself.
	 *
	 * <p>What comes back is not the draft. The server's copy is the truth about what the book says –
	 * putting the torn page back would be this mod quietly undoing the plugin – so the pages are the
	 * pages that arrived, and what is taken from the draft is everything the pages cannot carry: the
	 * name of the book, its history, and the formatting of each page that is still there.
	 */
	@Nullable
	public static QuillDocument reopen(List<String> pages, com.glamardor.roleplayersquill.text.Layout.Options options) {
		if (pages.isEmpty() || isBlank(pages)) {
			return null;
		}
		List<String> marks = marksOf(pages);
		Entry best = null;
		int bestScore = 0;
		for (Entry entry : index()) {
			if (entry.signed) {
				// A copy of somebody else's book, kept only to be found by a search – reopening a new
				// book as it would hand this one's title and history to a book that merely says the
				// same thing, a form letter typed again being the case that actually happens.
				continue;
			}
			int score = 0;
			for (String mark : marks) {
				if (entry.marks != null && entry.marks.contains(mark)) {
					score++;
				}
			}
			if (score > bestScore || score == bestScore && best != null && entry.when > best.when) {
				if (score > 0) {
					best = entry;
					bestScore = score;
				}
			}
		}
		// Half the pages, and never on the strength of a single page unless that is most of the book.
		// One page in common is two books quoting the same decree far more often than it is one book.
		if (best == null || bestScore * 2 < Math.min(marks.size(), best.marks.size())
				|| bestScore < 2 && marks.size() > 2) {
			return null;
		}
		QuillDocument draft = readDocument(draftDir().resolve(best.key + ".json"));
		if (draft == null) {
			return null;
		}

		// What each page of the draft is written as, so the pages that survived can be recognised.
		java.util.Map<String, List<Paragraph>> byMark = new java.util.HashMap<>();
		for (List<Paragraph> page : draft.pages()) {
			String written = LegacyCodec.encode(page,
					com.glamardor.roleplayersquill.text.Layout.lay(page, options));
			byMark.putIfAbsent(markOf(written), page);
		}

		QuillDocument document = new QuillDocument();
		document.pages().clear();
		document.setId(draft.id());
		document.setTitle(draft.title());
		for (String page : pages) {
			List<Paragraph> kept = byMark.get(markOf(page));
			document.pages().add(kept != null ? QuillDocument.copyPage(kept) : LegacyCodec.decode(page));
		}
		if (document.pages().isEmpty()) {
			document.pages().add(QuillDocument.newPage());
		}
		for (List<Paragraph> page : document.pages()) {
			LegacyCodec.restoreLists(page);
		}
		document.loadHistory(draft.history());
		RoleplayersQuill.LOGGER.info("Recognised this book as {} by {} of its {} pages",
				best.id, bestScore, pages.size());
		return document;
	}

	/**
	 * What is written on a page, with everything that is only spacing taken out.
	 *
	 * <p>The codes go, runs of blanks become one, and empty lines go with them – so a page laid out
	 * again, justified differently or re-encoded by a later version of this mod still looks like the
	 * page it is. What is left is the words, which is the only part a reader would call the page.
	 */
	static String markOf(String encodedPage) {
		StringBuilder out = new StringBuilder();
		for (String line : LegacyCodec.strip(encodedPage).split("\n", -1)) {
			String tidy = line.replaceAll("\\s+", " ").strip();
			if (tidy.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append('\n');
			}
			out.append(tidy);
		}
		return Integer.toHexString(out.toString().hashCode()) + ":" + out.length();
	}

	private static List<String> marksOf(List<String> pages) {
		List<String> marks = new ArrayList<>();
		for (String page : pages) {
			String mark = markOf(page);
			// A blank page is every blank page; it says nothing about which book this is.
			if (!mark.endsWith(":0")) {
				marks.add(mark);
			}
		}
		return marks;
	}

	/** One book in the little index beside the drafts: its name, its newest draft, and its pages. */
	static final class Entry {
		String id;
		String key;
		long when;
		List<String> marks;
		/** A copy of somebody else's book, kept only to be found – never a candidate to reopen as. */
		boolean signed;
	}

	static final class IndexDto {
		int format;
		List<Entry> books;
	}

	private static Path indexFile() {
		return draftDir().resolve(INDEX_NAME);
	}

	private static final String INDEX_NAME = "index.json";

	/** Every file in the drafts folder except the little index that lists them. */
	private static boolean isDraftFile(Path file) {
		return Files.isRegularFile(file) && !file.getFileName().toString().equals(INDEX_NAME);
	}

	private static List<Entry> cachedIndex;

	private static List<Entry> index() {
		if (cachedIndex != null) {
			return cachedIndex;
		}
		List<Entry> books = new ArrayList<>();
		Path file = indexFile();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				IndexDto dto = GSON.fromJson(reader, IndexDto.class);
				if (dto != null && dto.books != null) {
					for (Entry entry : dto.books) {
						if (entry != null && entry.id != null && entry.key != null && entry.marks != null) {
							books.add(entry);
						}
					}
				}
			} catch (IOException | RuntimeException error) {
				RoleplayersQuill.LOGGER.debug("Could not read {}", file, error);
			}
		}
		cachedIndex = books;
		return cachedIndex;
	}

	/**
	 * Notes which pages this book has, under the name the book goes by.
	 *
	 * <p>One line per book rather than one per save: the fingerprint changes every time a word is
	 * written, the name does not, and an index with a line per save would be the very heap of
	 * duplicates the library had to be cured of.
	 */
	private static void rememberPages(String id, String key, List<String> pages, boolean signed) {
		// A draft is written within a second of every keystroke, and between real saves it is written
		// under the same fingerprint every time – so there is nothing to record and no reason to put
		// two hundred books' worth of page marks back on the disk once a second.
		for (Entry known : index()) {
			if (known.id.equals(id) && known.key.equals(key)) {
				return;
			}
		}
		List<Entry> books = new ArrayList<>(index());
		books.removeIf(entry -> entry.id.equals(id));
		Entry entry = new Entry();
		entry.id = id;
		entry.key = key;
		entry.when = System.currentTimeMillis();
		entry.marks = marksOf(pages);
		entry.signed = signed;
		books.add(entry);
		books.sort((a, b) -> Long.compare(b.when, a.when));
		while (books.size() > 300) {
			books.remove(books.size() - 1);
		}
		cachedIndex = books;

		IndexDto dto = new IndexDto();
		dto.format = FORMAT;
		dto.books = books;
		try (Writer writer = Files.newBufferedWriter(indexFile(), StandardCharsets.UTF_8)) {
			GSON.toJson(dto, writer);
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not write {}", indexFile(), error);
		}
	}

	/**
	 * The unsaved work on a blank book, if there is any and it is recent.
	 *
	 * <p>Offered rather than opened. The editor shows a button saying how much there is and how long
	 * ago it was; nothing is put in front of anybody without their having asked for it.
	 */
	@Nullable
	public static QuillDocument blankDraft(String owner) {
		Path file = draftDir().resolve(keyOf(List.of(), owner) + ".json");
		if (!Files.isRegularFile(file)
				|| System.currentTimeMillis() - savedAt(file) > BLANK_DRAFT_LIFE) {
			return null;
		}
		QuillDocument document = readDocument(file);
		if (document == null || document.pages().stream().allMatch(QuillDocument::isEmpty)) {
			return null;
		}
		for (List<Paragraph> page : document.pages()) {
			LegacyCodec.restoreLists(page);
		}
		return document;
	}

	/** How long unsaved work on a blank book is offered back: long enough to be a recovery. */
	private static final long BLANK_DRAFT_LIFE = 60L * 60L * 1000L;

	private static long savedAt(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException error) {
			return 0L;
		}
	}

	/** Keeps the last few hundred and lets the rest go: a draft is a convenience, not an archive. */
	// ---- the versions of a book -------------------------------------------------------------------

	/** One book as it stood at one moment: where it is kept, when it was, and how big it was. */
	public record Version(Path file, long when, int pages, String title) {
	}

	private static Path historyDir(String id) {
		return ensure(FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID)
				.resolve("history").resolve(id.replaceAll("[^A-Za-z0-9-]", "")));
	}

	/**
	 * Keeps the book as it stands, if it does not stand as it did last time.
	 *
	 * <p>Undo takes back what was done a minute ago. This is the other question – what the book said
	 * yesterday, before the evening's rewriting – and the answer has to survive the editor being
	 * closed, the history being spent and the book being written over twice since.
	 *
	 * <p>Only when the book is really written back, never while it is being typed: a version per
	 * keystroke is not a history, it is a log. And only when something changed, so that pressing Done
	 * twice does not fill the list with the same book.
	 */
	public static void keepVersion(QuillDocument document, List<String> encodedPages) {
		String signature = String.join(" ", encodedPages);
		List<Version> already = versions(document.id());
		if (!already.isEmpty() && signature.equals(String.join(" ", writtenOf(already.get(0).file())))) {
			return;
		}

		VersionDto dto = new VersionDto();
		dto.when = System.currentTimeMillis();
		dto.title = document.title();
		dto.pages = pagesToDto(document.pages());
		dto.written = new ArrayList<>(encodedPages);
		Path file = historyDir(document.id()).resolve(dto.when + ".json");
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(dto, writer);
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not keep a version at {}", file, error);
			return;
		}
		pruneVersions(document.id());
	}

	/** Every kept version of a book, newest first. */
	public static List<Version> versions(String id) {
		List<Version> out = new ArrayList<>();
		Path dir = historyDir(id);
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (var stream = Files.list(dir)) {
			for (Path file : stream.filter(Files::isRegularFile).toList()) {
				VersionDto dto = read(file);
				if (dto == null || dto.pages == null) {
					continue;
				}
				out.add(new Version(file, dto.when, dto.pages.size(), dto.title == null ? "" : dto.title));
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not read the versions of {}", id, error);
		}
		out.sort((a, b) -> Long.compare(b.when(), a.when()));
		return out;
	}

	/** A kept version, as pages ready to be put back into a book. */
	@Nullable
	public static List<List<Paragraph>> readVersion(Path file) {
		VersionDto dto = read(file);
		return dto == null || dto.pages == null ? null : pagesFromDto(dto.pages);
	}

	/** What a kept version was written as, so that restoring it can be compared with what is held. */
	public static List<String> writtenOf(Path file) {
		VersionDto dto = read(file);
		return dto == null || dto.written == null ? List.of() : dto.written;
	}

	@Nullable
	private static VersionDto read(Path file) {
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, VersionDto.class);
		} catch (IOException | com.google.gson.JsonParseException error) {
			return null;
		}
	}

	private static void pruneVersions(String id) {
		Path dir = historyDir(id);
		try (var stream = Files.list(dir)) {
			List<Path> files = new ArrayList<>(stream.filter(Files::isRegularFile).toList());
			if (files.size() <= 20) {
				return;
			}
			files.sort(java.util.Comparator.naturalOrder());
			for (int i = 0; i < files.size() - 20; i++) {
				Files.deleteIfExists(files.get(i));
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not tidy the versions of {}", id, error);
		}
	}

	static final class VersionDto {
		long when;
		String title;
		List<List<ParagraphDto>> pages;
		/** The book as it was sent, which is what tells one version from another. */
		List<String> written;
	}

	/**
	 * One book kept on this computer, as it was last written.
	 *
	 * @param file   where it lies, so that a book can be read back whole or taken off the shelf
	 * @param signed a book that was read rather than written here
	 * @param copies every file this one book is in, its own among them; see {@link #allBooks}
	 */
	public record Kept(String name, QuillDocument document, long when, Path file, boolean signed,
			List<Path> copies) {
		/** Who signed it, when anybody did. */
		public String author() {
			return document.author();
		}
	}

	/**
	 * Every book this computer remembers, newest first.
	 *
	 * <p>Drafts are kept for every book that has been opened, which makes them, between them, the
	 * library: "the book where I wrote about the Flavian chapters" is a question about a hundred and
	 * fifty files rather than about the one in hand. They are small, and there are at most a hundred
	 * and fifty of them, so reading the lot is cheaper than being clever about it.
	 *
	 * <p>One book, though, is one book. A draft is filed under the pages the server holds, so every
	 * time a book is written back it is filed afresh and the one before it stays where it was: a
	 * book worked on all evening leaves a file per save, all of them the same book. Only the newest
	 * of each is a book here – anything older is an earlier version of it, which is what the history
	 * is for and not what a search through the shelves should be answering with.
	 */
	public static List<Kept> allBooks() {
		List<Kept> out = new ArrayList<>();
		// Both shelves: the books that were read, which are kept on purpose, and the drafts of the
		// books that were written, which are kept because they had to be kept anyway.
		gather(libraryDir(), true, out);
		gather(draftDir(), false, out);
		out.sort((a, b) -> Long.compare(b.when(), a.when()));

		// Twice over: the same book, and a different book that is word for word the same book. The
		// first catches a book saved every ten minutes all evening; the second catches the copy an
		// older version of this mod filed among the drafts beside the copy this one files on the
		// shelf, which are one reading of one book and have no business being two lines in a list.
		//
		// The files that lose are not forgotten, they are gathered onto the one that wins. A row on
		// the shelf is a book, and what is done to a book has to be done to every file it is in –
		// otherwise removing a book leaves its twin on disk to come back as a new row tomorrow, and
		// renaming one leaves a differently named copy of it standing right behind it.
		java.util.Map<String, String> group = new java.util.HashMap<>();
		java.util.LinkedHashMap<String, List<Kept>> together = new java.util.LinkedHashMap<>();
		for (Kept book : out) {
			// Every file of one book goes wherever that book's newest file went, whatever an older
			// file of it happens to say – it is the same book by the only name a book has.
			String key = group.computeIfAbsent(book.document().id(), id -> wordsOf(book.document()));
			together.computeIfAbsent(key, k -> new ArrayList<>()).add(book);
		}

		List<Kept> shelf = new ArrayList<>(together.size());
		for (List<Kept> same : together.values()) {
			// The newest of them, except that a book written here beats a copy of it that was read
			// back: a draft still holds the links and the exact colours that a page of a signed book
			// can no longer carry, so it is the better of two copies of the same words.
			Kept best = same.get(0);
			for (Kept other : same) {
				if (best.signed() && !other.signed()) {
					best = other;
					break;
				}
			}
			List<Path> copies = new ArrayList<>(same.size());
			for (Kept one : same) {
				copies.add(one.file());
			}
			shelf.add(new Kept(best.name(), best.document(), best.when(), best.file(), best.signed(),
					List.copyOf(copies)));
		}
		return shelf;
	}

	// ---- what a book on the shelf is called --------------------------------------------------------

	/**
	 * Gives a kept book a different name.
	 *
	 * <p>A book read off a server is called whatever whoever signed it called it, and a draft is
	 * called by the first line anybody happened to type – neither of which is necessarily what it is
	 * to the person who kept it. "Указ о пошлинах" beats "Без названия" in a list of two hundred, and
	 * the name travels: restoring the book into a blank one puts this name on it.
	 *
	 * <p>Every file the book is in is renamed, or the twin left behind would stand beside it in the
	 * list under the old name. The date is put back afterwards: the shelf is ordered by when a book
	 * was last written, and renaming a book is not writing it.
	 */
	public static boolean rename(Kept book, String title) {
		String trimmed = title.length() > QuillDocument.MAX_TITLE
				? title.substring(0, QuillDocument.MAX_TITLE) : title;
		boolean done = true;
		for (Path file : book.copies()) {
			done &= retitle(file, trimmed);
		}
		if (done) {
			book.document().setTitle(trimmed);
		}
		return done;
	}

	private static boolean retitle(Path file, String title) {
		DocumentDto dto = readDocumentDto(file);
		if (dto == null) {
			return false;
		}
		dto.title = title;
		try {
			var was = Files.getLastModifiedTime(file);
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(dto, writer);
			}
			Files.setLastModifiedTime(file, was);
			return true;
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not rename {}", file, error);
			return false;
		}
	}

	/**
	 * Everything a reader would call the book: its name, who signed it, and what it says.
	 *
	 * <p>Two books are the same book here when nothing a reader could point at is different. The
	 * spacing is not one of those things – it is how the page was justified on whatever machine
	 * wrote it – so it comes out, along with the codes, exactly as in {@link #markOf}. What is left
	 * is the words, in the pages they are on.
	 *
	 * <p>Deliberately not forgiving beyond that. Two drafts of one book differ by a word somewhere,
	 * which is the whole reason there are two of them, and that word is enough to keep them apart.
	 */
	private static String wordsOf(QuillDocument document) {
		StringBuilder out = new StringBuilder(document.title()).append(' ').append(document.author());
		for (List<Paragraph> page : document.pages()) {
			out.append('\f');
			for (Paragraph paragraph : page) {
				String tidy = LegacyCodec.strip(paragraph.text()).replaceAll("\\s+", " ").strip();
				if (!tidy.isEmpty()) {
					out.append(tidy).append('\n');
				}
			}
		}
		return out.toString();
	}

	/** Every book in one folder, read whole. */
	private static void gather(Path dir, boolean shelf, List<Kept> into) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var stream = Files.list(dir)) {
			for (Path file : stream.filter(BookIO::isDraftFile).toList()) {
				DocumentDto dto = readDocumentDto(file);
				if (dto == null) {
					continue;
				}
				// Copies of other people's books used to be filed among the drafts, so which folder a
				// book is in does not settle what it is; the file says so itself.
				boolean signed = shelf || dto.signed;
				QuillDocument document = fromDto(dto);
				if (document.pageCount() == 0) {
					continue;
				}
				long when;
				try {
					when = Files.getLastModifiedTime(file).toMillis();
				} catch (IOException error) {
					when = 0L;
				}
				into.add(new Kept(nameOf(document), document, when, file, signed, List.of(file)));
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not read the books in {}", dir, error);
		}
	}

	/** What to call a book in a list: its title, or the first thing written in it. */
	public static String nameOf(QuillDocument document) {
		if (!document.title().isBlank()) {
			return document.title();
		}
		for (Paragraph paragraph : document.page(0)) {
			if (!paragraph.text().isBlank()) {
				String line = paragraph.text().strip();
				return line.length() > 40 ? line.substring(0, 40) + "…" : line;
			}
		}
		return "";
	}

	private static void pruneDrafts() {
		// Fewer than there used to be, because a draft now carries its history with it and is a good
		// deal heavier for it. A hundred and fifty books is still every book anyone has written this
		// month.
		prune(draftDir(), 150);
	}

	/**
	 * The shelf holds a great deal more than the drafts do, and for a different reason.
	 *
	 * <p>A draft is the working copy of something that exists elsewhere; the shelf is where the only
	 * copy of a burnt book is. So the ceiling is the one that keeps the folder from growing without
	 * end rather than one that expects to be reached, and what goes first is whatever has not been
	 * opened for longest.
	 */
	private static void pruneLibrary() {
		prune(libraryDir(), 600);
	}

	private static void prune(Path dir, int keep) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var stream = Files.list(dir)) {
			List<Path> files = new ArrayList<>(stream.filter(BookIO::isDraftFile).toList());
			int over = files.size() - keep;
			if (over <= 0) {
				return;
			}
			files.sort((a, b) -> {
				try {
					return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
				} catch (IOException error) {
					return 0;
				}
			});
			for (int i = 0; i < files.size() && over > 0; i++) {
				// A starred book is not tidied away, however long ago it was last opened. The ceiling
				// is there to stop a folder growing without end; a book somebody went to the trouble
				// of starring is the opposite of the thing the ceiling is aimed at, and it may well
				// be the last copy of something. So the oldest unstarred book goes instead, and if
				// they are all starred, nothing goes.
				if (isStarred(files.get(i))) {
					continue;
				}
				Files.deleteIfExists(files.get(i));
				over--;
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not tidy {}", dir, error);
		}
	}

	/** Whether the book in this file is one of the starred ones. */
	private static boolean isStarred(Path file) {
		if (favourites().isEmpty()) {
			return false;
		}
		DocumentDto dto = readDocumentDto(file);
		return dto != null && dto.id != null && isFavourite(dto.id);
	}

	// ---- the wire format ---------------------------------------------------------------------------

	private static DocumentDto toDto(QuillDocument document) {
		DocumentDto dto = new DocumentDto();
		dto.format = FORMAT;
		dto.title = document.title();
		dto.author = document.author();
		dto.lore = document.lore().isEmpty() ? null : new ArrayList<>(document.lore());
		dto.id = document.id();
		dto.pages = pagesToDto(document.pages());
		dto.history = new ArrayList<>();
		for (QuillDocument.State state : document.history()) {
			HistoryDto step = new HistoryDto();
			step.title = state.title();
			step.pages = pagesToDto(state.pages());
			dto.history.add(step);
		}
		return dto;
	}

	private static List<List<ParagraphDto>> pagesToDto(List<List<Paragraph>> pages) {
		List<List<ParagraphDto>> out = new ArrayList<>();
		for (List<Paragraph> page : pages) {
			List<ParagraphDto> paragraphs = new ArrayList<>();
			for (Paragraph paragraph : page) {
				paragraphs.add(toDto(paragraph));
			}
			out.add(paragraphs);
		}
		return out;
	}

	private static List<List<Paragraph>> pagesFromDto(List<List<ParagraphDto>> pages) {
		List<List<Paragraph>> out = new ArrayList<>();
		for (List<ParagraphDto> page : pages) {
			List<Paragraph> paragraphs = new ArrayList<>();
			for (ParagraphDto paragraph : page) {
				paragraphs.add(fromDto(paragraph));
			}
			if (paragraphs.isEmpty()) {
				paragraphs.add(new Paragraph());
			}
			out.add(paragraphs);
		}
		return out;
	}

	private static ParagraphDto toDto(Paragraph paragraph) {
		ParagraphDto dto = new ParagraphDto();
		dto.text = paragraph.text();
		dto.align = paragraph.alignment().id();
		dto.list = paragraph.list().id();
		dto.indent = paragraph.indent();
		dto.runs = new ArrayList<>();
		// Characters that share a style share a run, which is how a page of ordinary prose ends up
		// as one line of JSON instead of a thousand.
		int start = 0;
		while (start < paragraph.length()) {
			QuillStyle style = paragraph.styleAt(start);
			int end = start + 1;
			while (end < paragraph.length() && paragraph.styleAt(end).equals(style)) {
				end++;
			}
			if (!style.equals(QuillStyle.PLAIN)) {
				RunDto run = new RunDto();
				run.start = start;
				run.end = end;
				run.bold = style.bold();
				run.italic = style.italic();
				run.underlined = style.underlined();
				run.strikethrough = style.strikethrough();
				run.obfuscated = style.obfuscated();
				run.color = style.color();
				run.url = style.url();
				run.hover = style.hover();
				run.command = style.command();
				run.copy = style.copy();
				run.page = style.page();
				dto.runs.add(run);
			}
			start = end;
		}
		return dto;
	}

	private static QuillDocument fromDto(DocumentDto dto) {
		QuillDocument document = new QuillDocument();
		document.pages().clear();
		document.setTitle(dto.title == null ? "" : dto.title);
		document.setAuthor(dto.author);
		document.setLore(dto.lore);
		document.setId(dto.id);
		if (dto.pages == null || dto.pages.isEmpty()) {
			document.pages().add(QuillDocument.newPage());
			return document;
		}
		document.pages().addAll(pagesFromDto(dto.pages));
		if (dto.history != null && !dto.history.isEmpty()) {
			List<QuillDocument.State> states = new ArrayList<>();
			for (HistoryDto step : dto.history) {
				if (step != null && step.pages != null) {
					states.add(new QuillDocument.State(pagesFromDto(step.pages),
							step.title == null ? "" : step.title));
				}
			}
			document.loadHistory(states);
		}
		return document;
	}

	private static Paragraph fromDto(ParagraphDto dto) {
		Paragraph paragraph = new Paragraph();
		String text = dto.text == null ? "" : dto.text;
		if (!text.isEmpty()) {
			paragraph.insert(0, text, QuillStyle.PLAIN);
		}
		paragraph.setAlignment(Alignment.byId(dto.align == null ? "left" : dto.align));
		paragraph.setList(ListStyle.byId(dto.list == null ? "none" : dto.list));
		paragraph.setIndent(dto.indent);
		if (dto.runs != null) {
			for (RunDto run : dto.runs) {
				QuillStyle style = new QuillStyle(run.bold, run.italic, run.underlined, run.strikethrough,
						run.obfuscated, run.color, run.url, run.hover, run.command, run.copy, run.page);
				paragraph.restyle(run.start, run.end, existing -> style);
			}
		}
		return paragraph;
	}

	// Plain field-bearing classes rather than records: Gson has handled these since it was written,
	// and a config file is no place to find out which version of it a launcher shipped.

	static final class DocumentDto {
		int format;
		String title;
		/** Who signed the book, where it was signed at all. */
		String author;
		/** Whatever the item carried under its name, kept as written with its codes. */
		List<String> lore;
		/** What the book is called between sessions; see QuillDocument.id. */
		String id;
		List<List<ParagraphDto>> pages;
		/** The steps back, newest first, so that undo still works after the book has been shut. */
		List<HistoryDto> history;
		/** A copy of somebody else's book kept by {@link #keepSigned}; see {@link Entry#signed}. */
		boolean signed;
	}

	static final class HistoryDto {
		String title;
		List<List<ParagraphDto>> pages;
	}

	static final class ParagraphDto {
		String text;
		String align;
		String list;
		int indent;
		List<RunDto> runs;
	}

	static final class RunDto {
		int start;
		int end;
		boolean bold;
		boolean italic;
		boolean underlined;
		boolean strikethrough;
		boolean obfuscated;
		int color = QuillStyle.INHERIT;
		String url;
		String hover;
		String command;
		String copy;
		int page;
	}
}
