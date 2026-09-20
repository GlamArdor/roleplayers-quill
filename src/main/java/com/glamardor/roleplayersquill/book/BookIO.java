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
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			DocumentDto dto = GSON.fromJson(reader, DocumentDto.class);
			return dto == null ? null : fromDto(dto);
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
		Path file = draftDir().resolve(keyOf(encodedPages, owner) + ".json");
		try {
			writeDocument(document, file);
			pruneDrafts();
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not keep the draft at {}", file, error);
		}
	}

	@Nullable
	public static QuillDocument loadDraft(List<String> pages, String owner) {
		Path file = draftDir().resolve(keyOf(pages, owner) + ".json");
		if (!Files.isRegularFile(file)) {
			return null;
		}
		QuillDocument document = readDocument(file);
		if (document == null) {
			return null;
		}
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

	/** One book kept on this computer, as it was last written. */
	public record Kept(String name, QuillDocument document, long when) {
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
		Path dir = draftDir();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (var stream = Files.list(dir)) {
			for (Path file : stream.filter(Files::isRegularFile).toList()) {
				QuillDocument document = readDocument(file);
				if (document == null || document.pageCount() == 0) {
					continue;
				}
				long when;
				try {
					when = Files.getLastModifiedTime(file).toMillis();
				} catch (IOException error) {
					when = 0L;
				}
				out.add(new Kept(nameOf(document), document, when));
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not read the drafts", error);
		}
		out.sort((a, b) -> Long.compare(b.when(), a.when()));

		List<Kept> newest = new ArrayList<>(out.size());
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Kept book : out) {
			if (seen.add(book.document().id())) {
				newest.add(book);
			}
		}
		return newest;
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
		Path dir = draftDir();
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var stream = Files.list(dir)) {
			List<Path> files = new ArrayList<>(stream.filter(Files::isRegularFile).toList());
			// Fewer than there used to be, because a draft now carries its history with it and is a
			// good deal heavier for it. A hundred and fifty books is still every book anyone has
			// written this month.
			if (files.size() <= 150) {
				return;
			}
			files.sort((a, b) -> {
				try {
					return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
				} catch (IOException error) {
					return 0;
				}
			});
			for (int i = 0; i < files.size() - 150; i++) {
				Files.deleteIfExists(files.get(i));
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.debug("Could not tidy the drafts", error);
		}
	}

	// ---- the wire format ---------------------------------------------------------------------------

	private static DocumentDto toDto(QuillDocument document) {
		DocumentDto dto = new DocumentDto();
		dto.format = FORMAT;
		dto.title = document.title();
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
		/** What the book is called between sessions; see QuillDocument.id. */
		String id;
		List<List<ParagraphDto>> pages;
		/** The steps back, newest first, so that undo still works after the book has been shut. */
		List<HistoryDto> history;
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
