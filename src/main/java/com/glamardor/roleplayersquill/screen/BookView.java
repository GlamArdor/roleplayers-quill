package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.QuillDocument;

import java.util.List;

/**
 * The slice of an editor that {@link FindBar}, {@link PagesScreen} and {@link ExportScreen} need:
 * which page is open, what document it belongs to, and how to turn it into the strings a book
 * actually carries.
 *
 * <p>{@link PageEditor} is the only implementation that can change anything. A signed book being
 * read implements this too – see {@code reader.ReadDocument} – with {@link #editable()} false, so
 * that finding, jumping to a page and exporting work over a book nobody can write to without a
 * second, parallel set of screens built to do the same three things. Everything a read-only
 * implementation cannot do is a default here that does nothing, rather than a method the three
 * screens have to ask permission before calling.
 */
public interface BookView {
	QuillDocument document();

	int page();

	void setPage(int index);

	List<String> encodePages();

	/** Whether this book can be changed. A signed book being read never can. */
	default boolean editable() {
		return false;
	}

	/** Moves the caret to a match; does nothing where there is no caret to move. */
	default void setCaret(int paragraphIndex, int index, boolean extend) {
	}

	/** Marks the book as touched; does nothing where nothing can be saved. */
	default void touch() {
	}

	/** Only ever called from behind {@link #editable()}; a read-only view never has to implement it. */
	default void duplicatePage() {
	}

	/** Only ever called from behind {@link #editable()}; a read-only view never has to implement it. */
	default void removePage() {
	}
}
