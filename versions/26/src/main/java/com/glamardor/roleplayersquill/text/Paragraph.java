package com.glamardor.roleplayersquill.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * One block of text with one alignment: what the player makes by pressing enter.
 *
 * <p>The style is kept per character rather than as a list of runs. A page holds a thousand
 * characters at the very most, so the memory is nothing, and every editing operation – insert in
 * the middle, delete across a boundary, paint a selection – becomes an array splice instead of a
 * run-merging exercise that is wrong in three places the first time anybody writes it.
 */
public final class Paragraph {
	private final StringBuilder text = new StringBuilder();
	private final List<QuillStyle> styles = new ArrayList<>();

	private Alignment alignment = Alignment.LEFT;
	private ListStyle list = ListStyle.NONE;
	/** Extra indent in whole characters' worth of space, applied to every line of the paragraph. */
	private int indent;
	private FrameStyle frame = FrameStyle.NONE;

	/**
	 * Set on the far half of a paragraph that a page break cut in two.
	 *
	 * <p>It keeps the list marker from being printed twice and the numbering from counting the same
	 * item again, which is what pagination would otherwise do to any list long enough to need it.
	 */
	private boolean continuation;
	/** Set on the near half: its last line is not really a last line, so justification still applies. */
	private boolean continues;

	public Paragraph() {
	}

	public Paragraph(String initial, QuillStyle style) {
		insert(0, initial, style);
	}

	// ---- shape ----------------------------------------------------------------------------------

	public int length() {
		return text.length();
	}

	public boolean isEmpty() {
		return text.isEmpty();
	}

	public String text() {
		return text.toString();
	}

	public char charAt(int index) {
		return text.charAt(index);
	}

	public QuillStyle styleAt(int index) {
		if (styles.isEmpty()) {
			return QuillStyle.PLAIN;
		}
		return styles.get(Math.max(0, Math.min(index, styles.size() - 1)));
	}

	/**
	 * The style a character typed at this caret position should take: the one to the left, which is
	 * what every word processor does and what makes carrying formatting forward feel automatic.
	 */
	public QuillStyle styleBefore(int caret) {
		if (styles.isEmpty()) {
			return QuillStyle.PLAIN;
		}
		return styles.get(Math.max(0, Math.min(caret, styles.size()) - 1));
	}

	public Alignment alignment() {
		return alignment;
	}

	public void setAlignment(Alignment alignment) {
		this.alignment = alignment;
	}

	public ListStyle list() {
		return list;
	}

	public void setList(ListStyle list) {
		this.list = list;
	}

	/**
	 * The frame this line is written inside, or none.
	 *
	 * <p>A property of the paragraph rather than of the page, because a page is a bare list of
	 * paragraphs with nowhere to keep anything – and because carried this way it survives being
	 * copied, moved between pages and split in two, which a page-level flag would not.
	 */
	public FrameStyle frame() {
		return frame;
	}

	public void setFrame(FrameStyle frame) {
		this.frame = frame == null ? FrameStyle.NONE : frame;
	}

	public int indent() {
		return indent;
	}

	public void setIndent(int indent) {
		this.indent = Math.max(0, Math.min(8, indent));
	}

	public boolean isContinuation() {
		return continuation;
	}

	public void setContinuation(boolean value) {
		this.continuation = value;
	}

	public boolean continues() {
		return continues;
	}

	public void setContinues(boolean value) {
		this.continues = value;
	}

	// ---- editing --------------------------------------------------------------------------------

	public void insert(int index, String value, QuillStyle style) {
		int at = clamp(index);
		text.insert(at, value);
		List<QuillStyle> added = new ArrayList<>(value.length());
		for (int i = 0; i < value.length(); i++) {
			added.add(style);
		}
		styles.addAll(at, added);
	}

	/** Inserts a run that already carries its own per-character styling. */
	public void insert(int index, String value, List<QuillStyle> runStyles) {
		int at = clamp(index);
		text.insert(at, value);
		List<QuillStyle> added = new ArrayList<>(value.length());
		for (int i = 0; i < value.length(); i++) {
			added.add(i < runStyles.size() ? runStyles.get(i) : QuillStyle.PLAIN);
		}
		styles.addAll(at, added);
	}

	public void delete(int from, int to) {
		int start = clamp(Math.min(from, to));
		int end = clamp(Math.max(from, to));
		if (start == end) {
			return;
		}
		text.delete(start, end);
		styles.subList(start, end).clear();
	}

	public void restyle(int from, int to, UnaryOperator<QuillStyle> change) {
		int start = clamp(Math.min(from, to));
		int end = clamp(Math.max(from, to));
		for (int i = start; i < end; i++) {
			styles.set(i, change.apply(styles.get(i)));
		}
	}

	/** Cuts this paragraph in two, leaving the first half here and returning the second. */
	public Paragraph split(int index) {
		int at = clamp(index);
		Paragraph tail = new Paragraph();
		tail.alignment = alignment;
		tail.list = list;
		tail.indent = indent;
		tail.frame = frame;
		tail.text.append(text, at, text.length());
		tail.styles.addAll(styles.subList(at, styles.size()));
		text.delete(at, text.length());
		styles.subList(at, styles.size()).clear();
		return tail;
	}

	/** Pours another paragraph onto the end of this one, keeping this one's alignment. */
	public void append(Paragraph other) {
		text.append(other.text);
		styles.addAll(other.styles);
	}

	public Paragraph slice(int from, int to) {
		int start = clamp(Math.min(from, to));
		int end = clamp(Math.max(from, to));
		Paragraph out = new Paragraph();
		out.alignment = alignment;
		out.list = list;
		out.indent = indent;
		out.frame = frame;
		out.continuation = continuation;
		out.continues = continues;
		out.text.append(text, start, end);
		out.styles.addAll(styles.subList(start, end));
		return out;
	}

	public Paragraph copy() {
		return slice(0, text.length());
	}

	public List<QuillStyle> stylesIn(int from, int to) {
		int start = clamp(Math.min(from, to));
		int end = clamp(Math.max(from, to));
		return new ArrayList<>(styles.subList(start, end));
	}

	private int clamp(int index) {
		return Math.max(0, Math.min(index, text.length()));
	}
}
