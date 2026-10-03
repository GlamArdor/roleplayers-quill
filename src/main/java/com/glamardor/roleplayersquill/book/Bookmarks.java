package com.glamardor.roleplayersquill.book;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One bookmark per book, kept on this computer and nowhere else.
 *
 * <p>Never in the book itself. A book is a list of strings the server hands to everyone who opens
 * it, so a bookmark written into it would be a bookmark in everybody's copy, and a change to the
 * book that this mod would then have to send – which is exactly what a ribbon in a real book is
 * not. So it lives in a small file beside the drafts, filed under what the book is:
 *
 * <ul>
 * <li>a book still being written under its {@link QuillDocument#id()}, which is carried in its
 * draft and survives both writing in it and a page being torn out of it;</li>
 * <li>a signed book under a fingerprint of its title, its author and every word in it, since a
 * signed book never changes again and every copy of it is the same book.</li>
 * </ul>
 *
 * <p>The page is kept twice: by number, and by what is written on it. A book being written has
 * pages put in front of the marked one and taken out from in front of it, and the number alone
 * would then mark a different page; the words alone would lose the mark the moment the page itself
 * was rewritten. The words are asked first, and the number answers when they cannot.
 */
public final class Bookmarks {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FORMAT = 1;
	/** Plenty for every book anybody reads, and a file that never grows past a few hundred kilobytes. */
	private static final int KEPT = 2000;
	/** Enough of a page to tell it from its neighbours, and short enough to keep two thousand of. */
	private static final int MARK_LENGTH = 120;

	@Nullable
	private static Map<String, Saved> cache;

	private Bookmarks() {
	}

	// ---- what a book is filed under ---------------------------------------------------------------

	public static String keyOf(QuillDocument document) {
		return "book:" + document.id();
	}

	/** A signed book, by everything that makes it the book it is. Every copy of it gives the same key. */
	public static String keyOfSigned(String title, String author, List<String> pages) {
		StringBuilder all = new StringBuilder(title).append('\0').append(author);
		for (String page : pages) {
			all.append('\0').append(page);
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest(all.toString().getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder("signed:");
			for (byte b : digest) {
				hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException impossible) {
			// Every Java has SHA-1; this is only here because the signature says it might not.
			return "signed:" + Integer.toHexString(all.toString().hashCode());
		}
	}

	/** What is written on a page, spacing aside, cut short – what the page is recognised by. */
	public static String markOf(List<Paragraph> page) {
		StringBuilder out = new StringBuilder();
		for (Paragraph paragraph : page) {
			for (String word : paragraph.text().split("\\s+")) {
				if (word.isEmpty()) {
					continue;
				}
				if (!out.isEmpty()) {
					out.append(' ');
				}
				out.append(word);
				if (out.length() >= MARK_LENGTH) {
					return out.substring(0, MARK_LENGTH);
				}
			}
		}
		return out.toString();
	}

	// ---- asking and telling -------------------------------------------------------------------------

	/**
	 * Which page of this document the bookmark filed under the key is on, or -1 when there is none.
	 *
	 * <p>The page that still says what the marked page said, and the nearest such page to where the
	 * mark was if several do – a book of blank pages says the same thing on every one of them. When
	 * none does, the page that now stands where the marked one stood.
	 */
	public static int find(String key, QuillDocument document) {
		Saved saved = all().get(key);
		if (saved == null) {
			return -1;
		}
		int count = document.pageCount();
		if (saved.mark != null) {
			int best = -1;
			for (int i = 0; i < count; i++) {
				if (markOf(document.page(i)).equals(saved.mark)
						&& (best < 0 || Math.abs(i - saved.page) < Math.abs(best - saved.page))) {
					best = i;
				}
			}
			if (best >= 0) {
				return best;
			}
		}
		return Math.max(0, Math.min(saved.page, count - 1));
	}

	/** The marked page of a book whose pages never move, so the number is all there is to ask. */
	public static int find(String key, int pageCount) {
		Saved saved = all().get(key);
		return saved == null || pageCount <= 0 ? -1 : Math.max(0, Math.min(saved.page, pageCount - 1));
	}

	/** Marks a page, or takes the mark away with a page of -1. Written to disk at once, when it changed. */
	public static void put(String key, int page, @Nullable String mark) {
		Map<String, Saved> all = all();
		if (page < 0) {
			if (all.remove(key) != null) {
				write();
			}
			return;
		}
		Saved before = all.get(key);
		if (before != null && before.page == page && java.util.Objects.equals(before.mark, mark)) {
			return;
		}
		Saved saved = new Saved();
		saved.page = page;
		saved.mark = mark;
		saved.when = System.currentTimeMillis();
		// Moving a bookmark to another page is the same bookmark; it keeps the colour it was given.
		if (before != null) {
			saved.colour = before.colour;
			saved.style = before.style;
		}
		// Removed first so the map's order is the order things were last marked in, which is the
		// order the oldest are let go in.
		all.remove(key);
		all.put(key, saved);
		while (all.size() > KEPT) {
			all.remove(all.keySet().iterator().next());
		}
		write();
	}

	// ---- what it looks like ---------------------------------------------------------------------------

	/**
	 * The colour and the shape of one book's bookmark. Either can be null, which means "as the
	 * settings say" – so a bookmark nobody has dressed follows the settings when they change, and
	 * only one that was given its own colour in the book keeps it.
	 */
	public record Look(@Nullable QuillConfig.BookmarkColour colour, @Nullable QuillConfig.BookmarkStyle style) {
		public static final Look DEFAULT = new Look(null, null);

		public QuillConfig.BookmarkColour colourOrDefault() {
			return colour != null ? colour : QuillConfig.get().bookmarkColour;
		}

		public QuillConfig.BookmarkStyle styleOrDefault() {
			return style != null ? style : QuillConfig.get().bookmarkStyle;
		}
	}

	public static Look look(String key) {
		Saved saved = all().get(key);
		if (saved == null) {
			return Look.DEFAULT;
		}
		return new Look(parse(QuillConfig.BookmarkColour.class, saved.colour),
				parse(QuillConfig.BookmarkStyle.class, saved.style));
	}

	/** Dresses the bookmark of a book that has one; a book without one has nothing to dress. */
	public static void setLook(String key, Look look) {
		Saved saved = all().get(key);
		if (saved == null) {
			return;
		}
		saved.colour = look.colour() == null ? null : look.colour().name();
		saved.style = look.style() == null ? null : look.style().name();
		write();
	}

	@Nullable
	private static <E extends Enum<E>> E parse(Class<E> type, @Nullable String name) {
		if (name == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException unknown) {
			// A colour a later version added and this one has never heard of.
			return null;
		}
	}

	// ---- the file -------------------------------------------------------------------------------------

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID).resolve("bookmarks.json");
	}

	private static Map<String, Saved> all() {
		if (cache != null) {
			return cache;
		}
		Map<String, Saved> read = new LinkedHashMap<>();
		Path file = file();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				FileDto dto = GSON.fromJson(reader, FileDto.class);
				if (dto != null && dto.books != null) {
					List<Map.Entry<String, Saved>> entries = new ArrayList<>(dto.books.entrySet());
					entries.removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
					entries.sort((a, b) -> Long.compare(a.getValue().when, b.getValue().when));
					for (Map.Entry<String, Saved> entry : entries) {
						read.put(entry.getKey(), entry.getValue());
					}
				}
			} catch (IOException | RuntimeException error) {
				RoleplayersQuill.LOGGER.warn("Could not read {}", file, error);
			}
		}
		cache = read;
		return cache;
	}

	private static void write() {
		FileDto dto = new FileDto();
		dto.format = FORMAT;
		dto.books = new HashMap<>(all());
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(dto, writer);
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.warn("Could not write {}", file, error);
		}
	}

	static final class Saved {
		int page;
		/** Null for a signed book, whose pages never move. */
		@Nullable
		String mark;
		long when;
		/** The names of a {@link Look}'s colour and shape; null for "as the settings say". */
		@Nullable
		String colour;
		@Nullable
		String style;
	}

	static final class FileDto {
		int format;
		Map<String, Saved> books;
	}
}
