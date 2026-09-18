package com.glamardor.roleplayersquill.text;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole book as the editor holds it: pages of paragraphs, a title, and the history behind them.
 *
 * <p>Undo is a stack of whole-document snapshots rather than a list of reversible operations. A
 * page is a thousand characters at the outside and a book a hundred pages, so a snapshot is smaller
 * than a screenshot, and an editor that can copy formatting across a selection, repaginate an
 * import and renumber a list has far too many kinds of change for per-operation undo to stay
 * honest. Snapshots cannot be subtly wrong.
 */
public final class QuillDocument {
	/** The game's own limits, from {@code WritableBookContentComponent}. */
	public static final int MAX_PAGES = 100;
	public static final int MAX_PAGE_CHARS = 1024;
	public static final int MAX_TITLE = 32;

	private static final int HISTORY = 120;

	/** What an edit was, for deciding whether it belongs with the one before it. */
	public enum Change {
		/** Characters going in. */
		TYPE,
		/** Characters coming out. */
		DELETE,
		/** Anything else: formatting, pages, pasting, a table. Never joined to what came before. */
		OTHER
	}

	/** How long a pause ends a run of typing, so that undo takes back what was written after it. */
	private static final long GROUP_PAUSE = 1500L;

	private Change lastChange = Change.OTHER;
	private long lastMarked;

	private final List<List<Paragraph>> pages = new ArrayList<>();
	private String title = "";

	/**
	 * What this book is called between one session and the next.
	 *
	 * <p>Not the title – two books can share a title, and a title can be changed. Not the text
	 * either: the text is what a draft is filed under, and it is different the moment anybody writes
	 * a word, which is exactly when a book most needs to be recognised as the same book. So a name
	 * of its own, made when the book is first met and carried along in the draft beside it. It is
	 * what the saved versions of a book hang from.
	 */
	private String id = java.util.UUID.randomUUID().toString();

	public String id() {
		return id;
	}

	public void setId(String id) {
		if (id != null && !id.isBlank()) {
			this.id = id;
		}
	}

	private final Deque<Snapshot> undo = new ArrayDeque<>();
	private final Deque<Snapshot> redo = new ArrayDeque<>();

	public QuillDocument() {
		pages.add(newPage());
	}

	public static List<Paragraph> newPage() {
		List<Paragraph> page = new ArrayList<>();
		page.add(new Paragraph());
		return page;
	}

	// ---- pages that came from somewhere else -----------------------------------------------------

	/**
	 * Every page as it arrived, filed under what it reads as.
	 *
	 * <p>A book this mod did not write is somebody else's typing, and laying it out again is an
	 * opinion about it: the wrapping moves a word, blanks typed by hand become a real indent, and
	 * what comes back out is not what went in. On the page being written that is the whole point. On
	 * the other nineteen it is damage, and in a book somebody lent to be read it is damage they get
	 * back.
	 *
	 * <p>So a page still holding exactly what it arrived holding is written back as the very string
	 * it arrived as, whatever this mod would have made of it. Only a page actually edited is laid
	 * out again.
	 *
	 * <p>Filed by content rather than by number on purpose: pages are inserted, deleted, moved and
	 * pasted, so the place a page had on the way in says nothing about where it is on the way out.
	 * It also means that editing a page and then undoing back to where it started hands back the
	 * original string rather than this mod's idea of it, which is the same page by every test that
	 * matters.
	 */
	private final Map<String, String> asReceived = new HashMap<>();

	/** Remembers a page as the server handed it over. */
	public void rememberSource(List<Paragraph> page, String source) {
		asReceived.put(signature(page), source);
	}

	/** The string this page arrived as, or null if it is not the page that arrived. */
	@Nullable
	public String sourceOf(List<Paragraph> page) {
		return asReceived.get(signature(page));
	}

	/**
	 * Everything about a page that anybody could tell apart, in one string.
	 *
	 * <p>The length of the text goes in front of the text, so that no arrangement of what somebody
	 * typed can be read as the punctuation holding this together.
	 */
	private static String signature(List<Paragraph> page) {
		StringBuilder out = new StringBuilder();
		out.append(page.size());
		for (Paragraph paragraph : page) {
			out.append('/').append(paragraph.alignment())
					.append('/').append(paragraph.list())
					.append('/').append(paragraph.indent())
					.append('/').append(paragraph.frame())
					.append('/').append(paragraph.isContinuation())
					.append('/').append(paragraph.continues())
					.append('/').append(paragraph.length())
					.append('/').append(paragraph.text());
			for (int i = 0; i < paragraph.length(); i++) {
				out.append('/').append(paragraph.styleAt(i));
			}
		}
		return out.toString();
	}

	// ---- pages ----------------------------------------------------------------------------------

	public int pageCount() {
		return pages.size();
	}

	public List<Paragraph> page(int index) {
		return pages.get(Math.max(0, Math.min(index, pages.size() - 1)));
	}

	public List<List<Paragraph>> pages() {
		return pages;
	}

	public boolean canAddPage() {
		return pages.size() < MAX_PAGES;
	}

	public void insertPage(int at, List<Paragraph> page) {
		if (!canAddPage()) {
			return;
		}
		pages.add(Math.max(0, Math.min(at, pages.size())), page);
	}

	public void removePage(int at) {
		if (pages.size() <= 1) {
			pages.set(0, newPage());
			return;
		}
		pages.remove(Math.max(0, Math.min(at, pages.size() - 1)));
	}

	public void movePage(int from, int to) {
		if (from < 0 || from >= pages.size() || to < 0 || to >= pages.size() || from == to) {
			return;
		}
		pages.add(to, pages.remove(from));
	}

	public void clearPage(int at) {
		pages.set(Math.max(0, Math.min(at, pages.size() - 1)), newPage());
	}

	/** Drops the empty pages at the end, the way vanilla does before it signs a book. */
	public void trimTrailingEmptyPages() {
		while (pages.size() > 1 && isEmpty(pages.get(pages.size() - 1))) {
			pages.remove(pages.size() - 1);
		}
	}

	public static boolean isEmpty(List<Paragraph> page) {
		for (Paragraph paragraph : page) {
			if (!paragraph.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	// ---- title ----------------------------------------------------------------------------------

	public String title() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title;
	}

	// ---- history -------------------------------------------------------------------------------

	/** Remembers where things stand, just before something is about to change them. */
	public void mark() {
		mark(Change.OTHER);
	}

	/**
	 * Notes where the book stood before an edit, unless that edit belongs with the one before it.
	 *
	 * <p>A step back used to be a single character, so taking back a word meant pressing undo once
	 * per letter – which is not undo, it is typing backwards. A run of letters typed without a pause
	 * is one step now, and so is a run of backspaces. The run is broken by anything that is not the
	 * same kind of edit, by a pause, and by a space – so undo takes back a word at a time, which is
	 * the unit people actually think in.
	 */
	public void mark(Change change) {
		long now = System.currentTimeMillis();
		boolean joins = change != Change.OTHER
				&& change == lastChange
				&& now - lastMarked < GROUP_PAUSE
				&& !undo.isEmpty();
		lastChange = change;
		lastMarked = now;
		if (joins) {
			// The step already on the stack is from before this run started, which is where undo
			// should land. Nothing to push; only the way forward is lost, as with any edit.
			redo.clear();
			return;
		}
		undo.push(snapshot());
		while (undo.size() > HISTORY) {
			undo.removeLast();
		}
		redo.clear();
	}

	/** Ends the current run, so the next edit starts a step of its own. Called at a word's end. */
	public void endGroup() {
		lastChange = Change.OTHER;
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	public void undo() {
		if (undo.isEmpty()) {
			return;
		}
		redo.push(snapshot());
		restore(undo.pop());
	}

	public void redo() {
		if (redo.isEmpty()) {
			return;
		}
		undo.push(snapshot());
		restore(redo.pop());
	}

	private Snapshot snapshot() {
		List<List<Paragraph>> copy = new ArrayList<>(pages.size());
		for (List<Paragraph> page : pages) {
			copy.add(copyPage(page));
		}
		return new Snapshot(copy, title);
	}

	private void restore(Snapshot snapshot) {
		pages.clear();
		pages.addAll(snapshot.pages());
		title = snapshot.title();
	}

	public static List<Paragraph> copyPage(List<Paragraph> page) {
		List<Paragraph> copy = new ArrayList<>(page.size());
		for (Paragraph paragraph : page) {
			copy.add(paragraph.copy());
		}
		return copy;
	}

	private record Snapshot(List<List<Paragraph>> pages, String title) {
	}

	/** A moment in the book's history, for keeping one on disk. */
	public record State(List<List<Paragraph>> pages, String title) {
	}

	/**
	 * How many steps back are kept when the book is put away.
	 *
	 * <p>Fewer than are kept while it is open. Each one is a copy of the whole book, and the drafts
	 * are kept by the hundred; forty copies of an eighteen-page book, four hundred times over, is a
	 * folder nobody asked for.
	 */
	public static final int KEPT_HISTORY = 40;

	/** The steps back, newest first, ready to be written down. */
	public List<State> history() {
		List<State> out = new ArrayList<>();
		for (Snapshot snapshot : undo) {
			if (out.size() >= KEPT_HISTORY) {
				break;
			}
			out.add(new State(snapshot.pages(), snapshot.title()));
		}
		return out;
	}

	/** Puts a history back, newest first, as it was written down. */
	public void loadHistory(List<State> states) {
		undo.clear();
		redo.clear();
		for (int i = states.size() - 1; i >= 0; i--) {
			undo.push(new Snapshot(states.get(i).pages(), states.get(i).title()));
		}
	}
}
