package com.glamardor.roleplayersquill.text;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finding and replacing across the whole book.
 *
 * <p>Across the whole book rather than the page, which is the only version of this worth having:
 * a book is eighteen pages by the time anybody needs to rename a character in it, and finding the
 * places by hand is exactly the work being avoided.
 *
 * <p>Everything here works on the paragraphs, not on the written page. A page is only a string of
 * text and formatting codes by the time it leaves, and searching that would find matches inside the
 * codes and replace halves of them.
 */
public final class BookSearch {
	private BookSearch() {
	}

	/** Where something was found: the page, the paragraph in it, and the characters it covers. */
	public record Hit(int page, int paragraph, int from, int to) {
	}

	/**
	 * The first match at or after a point, wrapping round to the beginning.
	 *
	 * @param after where to start looking, usually the end of the previous match
	 * @return where it is, or null when the book does not contain it at all
	 */
	@Nullable
	public static Hit next(List<List<Paragraph>> pages, String needle, boolean matchCase, Hit after) {
		if (needle.isEmpty()) {
			return null;
		}
		// Twice round: once from the starting point to the end, once from the beginning back to it.
		for (int pass = 0; pass < 2; pass++) {
			for (int page = 0; page < pages.size(); page++) {
				List<Paragraph> current = pages.get(page);
				for (int index = 0; index < current.size(); index++) {
					int from = 0;
					if (pass == 0) {
						if (page < after.page() || page == after.page() && index < after.paragraph()) {
							continue;
						}
						if (page == after.page() && index == after.paragraph()) {
							from = after.to();
						}
					} else if (page > after.page() || page == after.page() && index > after.paragraph()) {
						continue;
					}
					int at = indexOf(current.get(index).text(), needle, from, matchCase);
					if (at >= 0) {
						return new Hit(page, index, at, at + needle.length());
					}
				}
			}
		}
		return null;
	}

	/**
	 * Every match in the book, in reading order.
	 *
	 * <p>A book is a hundred pages at the very outside, so there is no sense in being clever: having
	 * the whole list makes walking backwards and saying "third of eleven" a matter of arithmetic
	 * rather than of another search that has to agree with the first one.
	 */
	public static List<Hit> all(List<List<Paragraph>> pages, String needle, boolean matchCase) {
		List<Hit> hits = new ArrayList<>();
		if (needle.isEmpty()) {
			return hits;
		}
		for (int page = 0; page < pages.size(); page++) {
			List<Paragraph> current = pages.get(page);
			for (int index = 0; index < current.size(); index++) {
				String text = current.get(index).text();
				int at = indexOf(text, needle, 0, matchCase);
				while (at >= 0) {
					hits.add(new Hit(page, index, at, at + needle.length()));
					at = indexOf(text, needle, at + needle.length(), matchCase);
				}
			}
		}
		return hits;
	}

	/** The match before this one, wrapping round to the last. */
	@Nullable
	public static Hit previous(List<List<Paragraph>> pages, String needle, boolean matchCase, Hit before) {
		List<Hit> hits = all(pages, needle, matchCase);
		if (hits.isEmpty()) {
			return null;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			if (isBefore(hits.get(i), before)) {
				return hits.get(i);
			}
		}
		return hits.get(hits.size() - 1);
	}

	/** Which match this is, counting from one, or 0 when it is not one of them. */
	public static int ordinalOf(List<List<Paragraph>> pages, String needle, boolean matchCase, Hit hit) {
		List<Hit> hits = all(pages, needle, matchCase);
		for (int i = 0; i < hits.size(); i++) {
			Hit other = hits.get(i);
			if (other.page() == hit.page() && other.paragraph() == hit.paragraph()
					&& other.from() == hit.from()) {
				return i + 1;
			}
		}
		return 0;
	}

	private static boolean isBefore(Hit one, Hit other) {
		if (one.page() != other.page()) {
			return one.page() < other.page();
		}
		if (one.paragraph() != other.paragraph()) {
			return one.paragraph() < other.paragraph();
		}
		return one.from() < other.from();
	}

	/** How many times it occurs in the whole book. */
	public static int count(List<List<Paragraph>> pages, String needle, boolean matchCase) {
		if (needle.isEmpty()) {
			return 0;
		}
		int found = 0;
		for (List<Paragraph> page : pages) {
			for (Paragraph paragraph : page) {
				String text = paragraph.text();
				int at = indexOf(text, needle, 0, matchCase);
				while (at >= 0) {
					found++;
					at = indexOf(text, needle, at + needle.length(), matchCase);
				}
			}
		}
		return found;
	}

	/**
	 * Replaces every occurrence in the book.
	 *
	 * <p>The replacement takes the formatting of the first character it stands on, so that renaming
	 * a character inside a red heading leaves it red.
	 *
	 * @return how many were replaced
	 */
	public static int replaceAll(List<List<Paragraph>> pages, String needle, String replacement,
			boolean matchCase) {
		if (needle.isEmpty()) {
			return 0;
		}
		int done = 0;
		for (List<Paragraph> page : pages) {
			for (Paragraph paragraph : page) {
				int at = indexOf(paragraph.text(), needle, 0, matchCase);
				while (at >= 0) {
					replace(paragraph, at, at + needle.length(), replacement);
					done++;
					at = indexOf(paragraph.text(), needle, at + replacement.length(), matchCase);
				}
			}
		}
		return done;
	}

	/** Puts a run of text in place of another, keeping the formatting of what it replaces. */
	public static void replace(Paragraph paragraph, int from, int to, String replacement) {
		QuillStyle style = paragraph.styleAt(Math.min(from, Math.max(0, paragraph.length() - 1)));
		paragraph.delete(from, to);
		if (!replacement.isEmpty()) {
			paragraph.insert(from, replacement, style);
		}
	}

	private static int indexOf(String haystack, String needle, int from, boolean matchCase) {
		if (from > haystack.length()) {
			return -1;
		}
		if (matchCase) {
			return haystack.indexOf(needle, from);
		}
		return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), from);
	}
}
