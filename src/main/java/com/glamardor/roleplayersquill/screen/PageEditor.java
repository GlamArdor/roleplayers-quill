package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.text.Alignment;
import com.glamardor.roleplayersquill.text.AutoCorrect;
import com.glamardor.roleplayersquill.text.Columns;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.ListStyle;
import com.glamardor.roleplayersquill.text.Ornaments;
import com.glamardor.roleplayersquill.text.Paginator;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.ParagraphStyle;
import com.glamardor.roleplayersquill.text.QuillDocument;
import com.glamardor.roleplayersquill.text.QuillStyle;
import com.glamardor.roleplayersquill.text.Widths;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The document, the caret, and everything that can be done to the two of them.
 *
 * <p>Kept apart from the screen on purpose. The screen is buttons and pixels and can be rewritten
 * for a new version of the game; this is where a book is actually edited, and it knows nothing
 * about either.
 *
 * <h2>Overflowing a page</h2>
 *
 * <p>Vanilla simply refuses the keystroke that would make a page too long. That is defensible for a
 * text box and miserable for a book: the fifteenth line of a chapter is not a mistake, it is the
 * fifteenth line. So a page that overflows spills into the next one, and the page after that, the
 * same way the importer fills pages – and the caret goes with the text rather than staying behind
 * on the page it was typed on.
 */
public final class PageEditor {
	private final QuillDocument document;
	private final QuillConfig config = QuillConfig.get();

	private int page;
	private int paragraph;
	private int caret;
	private int anchorParagraph;
	private int anchorCaret;

	/** The style the next character typed will take, when it is not simply the one to the left. */
	@Nullable
	private QuillStyle pending;
	/** What the format brush picked up, or null when the brush is not loaded. */
	@Nullable
	private QuillStyle brush;

	private List<Layout.LaidLine> lines = List.of();
	private boolean laidOut;
	private boolean changed;

	public PageEditor(QuillDocument document) {
		this.document = document;
	}

	// ---- what the screen asks ---------------------------------------------------------------------

	public QuillDocument document() {
		return document;
	}

	public int page() {
		return page;
	}

	public void setPage(int index) {
		page = Math.max(0, Math.min(index, document.pageCount() - 1));
		paragraph = 0;
		caret = 0;
		clearSelection();
		invalidate();
	}

	public List<Paragraph> currentPage() {
		return document.page(page);
	}

	public List<Layout.LaidLine> lines() {
		if (!laidOut) {
			lines = Layout.lay(currentPage(), config.layoutOptions());
			laidOut = true;
		}
		return lines;
	}

	public void invalidate() {
		laidOut = false;
	}

	/** Whether anything has been changed since the book was last written back. */
	public boolean isDirty() {
		return changed;
	}

	public void markSaved() {
		changed = false;
	}

	public void touch() {
		changed = true;
		invalidate();
	}

	public int paragraphIndex() {
		return paragraph;
	}

	public int caret() {
		return caret;
	}

	public boolean hasSelection() {
		return anchorParagraph != paragraph || anchorCaret != caret;
	}

	public void clearSelection() {
		anchorParagraph = paragraph;
		anchorCaret = caret;
	}

	/** The selection with its ends the right way round. */
	public Span selection() {
		if (anchorParagraph < paragraph || anchorParagraph == paragraph && anchorCaret <= caret) {
			return new Span(anchorParagraph, anchorCaret, paragraph, caret);
		}
		return new Span(paragraph, caret, anchorParagraph, anchorCaret);
	}

	public record Span(int fromParagraph, int fromIndex, int toParagraph, int toIndex) {
		/** Nothing selected anywhere: a span no real selection is ever equal to. */
		public static final Span EMPTY = new Span(-1, -1, -1, -1);

		public boolean isEmpty() {
			return fromParagraph == toParagraph && fromIndex == toIndex;
		}
	}

	public void setCaret(int paragraphIndex, int index, boolean extend) {
		List<Paragraph> current = currentPage();
		paragraph = Math.max(0, Math.min(paragraphIndex, current.size() - 1));
		caret = Math.max(0, Math.min(index, current.get(paragraph).length()));
		// Pressing bold and then clicking somewhere else should not carry the bold along: the
		// armed style belongs to the place it was armed at.
		pending = null;
		if (!extend) {
			clearSelection();
		}
	}

	@Nullable
	public QuillStyle brush() {
		return brush;
	}

	public void setBrush(@Nullable QuillStyle style) {
		brush = style;
	}

	/** The style shown on the toolbar: what is selected, or what the next keystroke would be. */
	public QuillStyle activeStyle() {
		if (pending != null) {
			return pending;
		}
		Span span = selection();
		if (!span.isEmpty()) {
			Paragraph first = currentPage().get(span.fromParagraph());
			int at = Math.min(span.fromIndex(), Math.max(0, first.length() - 1));
			return first.length() == 0 ? QuillStyle.PLAIN : first.styleAt(at);
		}
		return currentPage().get(paragraph).styleBefore(caret);
	}

	public Alignment activeAlignment() {
		return currentPage().get(paragraph).alignment();
	}

	public ListStyle activeList() {
		return currentPage().get(paragraph).list();
	}

	// ---- typing -----------------------------------------------------------------------------------

	private QuillStyle styleForTyping() {
		if (pending != null) {
			return pending;
		}
		if (!config.carryFormatting) {
			return QuillStyle.PLAIN;
		}
		// The style to the left, minus anything clickable: typing past the end of a link should not
		// quietly extend the link.
		return currentPage().get(paragraph).styleBefore(caret).withoutInteraction();
	}

	public void insert(String value) {
		if (value.isEmpty()) {
			return;
		}
		document.mark();
		deleteSelectionQuietly();
		QuillStyle style = styleForTyping();
		currentPage().get(paragraph).insert(caret, value, style);
		caret += value.length();
		pending = null;
		clearSelection();
		afterEdit();
	}

	/**
	 * Types a character, putting right what was typed before it if a rule says so.
	 *
	 * <p>The correction and the character go in as one edit, so that one press of undo takes back
	 * the whole thing rather than leaving half a dash behind.
	 */
	public void type(char typed) {
		Paragraph target = currentPage().get(paragraph);
		AutoCorrect.Fix fix = hasSelection() ? null
				: AutoCorrect.apply(target.text().substring(0, caret), typed);
		if (fix == null) {
			document.mark(QuillDocument.Change.TYPE);
			deleteSelectionQuietly();
			target.insert(caret, String.valueOf(typed), styleForTyping());
			caret++;
			pending = null;
			clearSelection();
			// A space closes the run, so that undo takes back a word rather than a sentence.
			if (Character.isWhitespace(typed)) {
				document.endGroup();
			}
			afterEdit();
			return;
		}
		document.mark(QuillDocument.Change.TYPE);
		int back = Math.min(fix.back(), caret);
		if (back > 0) {
			target.delete(caret - back, caret);
			caret -= back;
		}
		target.insert(caret, fix.text(), styleForTyping());
		caret += fix.text().length();
		pending = null;
		clearSelection();
		afterEdit();
	}

	/** Inserts a run in a style of its own, which is how a link written into the text gets its colour. */
	public void insertWithStyle(String value, QuillStyle style) {
		if (value.isEmpty()) {
			return;
		}
		document.mark();
		deleteSelectionQuietly();
		currentPage().get(paragraph).insert(caret, value, style);
		caret += value.length();
		pending = null;
		clearSelection();
		afterEdit();
	}

	/** Drops a run of whole paragraphs in at the caret, splitting the one it lands in. */
	public void insertParagraphs(List<Paragraph> incoming) {
		if (incoming.isEmpty()) {
			return;
		}
		document.mark();
		deleteSelectionQuietly();
		List<Paragraph> current = currentPage();
		Paragraph host = current.get(paragraph);
		Paragraph tail = host.split(caret);

		if (incoming.size() == 1) {
			Paragraph only = incoming.get(0);
			host.insert(host.length(), only.text(), only.stylesIn(0, only.length()));
			caret = host.length();
			host.append(tail);
		} else {
			Paragraph first = incoming.get(0);
			host.insert(host.length(), first.text(), first.stylesIn(0, first.length()));
			int at = paragraph + 1;
			for (int i = 1; i < incoming.size(); i++) {
				current.add(at++, incoming.get(i).copy());
			}
			paragraph = at - 1;
			caret = current.get(paragraph).length();
			current.get(paragraph).append(tail);
		}
		clearSelection();
		afterEdit();
	}

	public void newParagraph() {
		document.mark();
		deleteSelectionQuietly();
		List<Paragraph> current = currentPage();
		Paragraph host = current.get(paragraph);
		Paragraph tail = host.split(caret);
		// An empty list item plus enter ends the list, the way every editor behaves.
		if (host.isEmpty() && host.list() != ListStyle.NONE && tail.isEmpty()) {
			host.setList(ListStyle.NONE);
			host.setIndent(0);
			pending = null;
			clearSelection();
			afterEdit();
			return;
		}
		current.add(paragraph + 1, tail);
		paragraph++;
		caret = 0;
		pending = null;
		clearSelection();
		afterEdit();
	}

	public void backspace(boolean wholeWord) {
		if (hasSelection()) {
			deleteSelection();
			return;
		}
		List<Paragraph> current = currentPage();
		if (caret == 0 && undoParagraphStyle(current.get(paragraph))) {
			afterEdit();
			return;
		}

		document.mark(QuillDocument.Change.DELETE);
		if (caret > 0) {
			int to = wholeWord ? wordStart(current.get(paragraph), caret) : caret - 1;
			// A run of backspaces is one step, but stopping at a space ends it: press undo and the
			// word you were taking out comes back, not the paragraph.
			if (Character.isWhitespace(current.get(paragraph).charAt(caret - 1))) {
				document.endGroup();
			}
			current.get(paragraph).delete(to, caret);
			caret = to;
		} else if (paragraph > 0) {
			Paragraph previous = current.get(paragraph - 1);
			int join = previous.length();
			previous.append(current.remove(paragraph));
			paragraph--;
			caret = join;
		} else {
			return;
		}
		clearSelection();
		afterEdit();
	}

	/**
	 * Backspace at the very start of an empty paragraph takes the paragraph's formatting off before
	 * it takes the paragraph away, which is what every word processor does.
	 *
	 * <p>Centre a line, change your mind, press backspace: the centring goes and the caret stays
	 * where it was. Without this the caret is dragged onto the end of the line above and the
	 * centring is still there, on a paragraph that no longer exists.
	 *
	 * <p>One thing at a time, in the order they were most likely added: the list marker, then the
	 * indent, then the alignment.
	 *
	 * @return true when something was undone and the deletion should not happen
	 */
	private boolean undoParagraphStyle(Paragraph target) {
		// The marker and the indent come off whether or not anything has been written yet. They are
		// what the caret is actually sitting behind at the start of a line, so backspace there means
		// them, and having to reach for a button on the toolbar for something backspace is holding
		// is the sort of thing that makes an editor feel like it is arguing.
		if (target.list() != ListStyle.NONE) {
			document.mark();
			target.setList(ListStyle.NONE);
			return true;
		}
		if (target.indent() > 0) {
			document.mark();
			target.setIndent(target.indent() - 1);
			return true;
		}
		// Alignment only on an empty line. On a written one backspace has to be able to join it to
		// the line above, and a centred paragraph would otherwise take three presses to reach it.
		if (!target.isEmpty()) {
			return false;
		}
		if (target.alignment() != Alignment.LEFT) {
			document.mark();
			target.setAlignment(Alignment.LEFT);
			return true;
		}
		return false;
	}

	public void deleteForward(boolean wholeWord) {
		if (hasSelection()) {
			deleteSelection();
			return;
		}
		document.mark();
		List<Paragraph> current = currentPage();
		Paragraph host = current.get(paragraph);
		if (caret < host.length()) {
			int to = wholeWord ? wordEnd(host, caret) : caret + 1;
			host.delete(caret, to);
		} else if (paragraph + 1 < current.size()) {
			host.append(current.remove(paragraph + 1));
		} else {
			return;
		}
		clearSelection();
		afterEdit();
	}

	public void deleteSelection() {
		if (!hasSelection()) {
			return;
		}
		document.mark();
		deleteSelectionQuietly();
		afterEdit();
	}

	private void deleteSelectionQuietly() {
		Span span = selection();
		if (span.isEmpty()) {
			return;
		}
		List<Paragraph> current = currentPage();
		if (span.fromParagraph() == span.toParagraph()) {
			current.get(span.fromParagraph()).delete(span.fromIndex(), span.toIndex());
		} else {
			Paragraph first = current.get(span.fromParagraph());
			Paragraph last = current.get(span.toParagraph());
			first.delete(span.fromIndex(), first.length());
			last.delete(0, span.toIndex());
			first.append(last);
			for (int i = span.toParagraph(); i > span.fromParagraph(); i--) {
				current.remove(i);
			}
		}
		paragraph = span.fromParagraph();
		caret = span.fromIndex();
		clearSelection();
	}

	// ---- formatting ---------------------------------------------------------------------------------

	/**
	 * Paints a selection, or – with nothing selected – arms the next keystroke.
	 *
	 * <p>Arming rather than doing nothing is the behaviour people expect from a word processor: press
	 * bold, type, and the typing is bold.
	 */
	public void restyle(UnaryOperator<QuillStyle> change) {
		Span span = selection();
		if (span.isEmpty()) {
			pending = change.apply(activeStyle());
			return;
		}
		document.mark();
		List<Paragraph> current = currentPage();
		if (span.fromParagraph() == span.toParagraph()) {
			current.get(span.fromParagraph()).restyle(span.fromIndex(), span.toIndex(), change);
		} else {
			Paragraph first = current.get(span.fromParagraph());
			first.restyle(span.fromIndex(), first.length(), change);
			for (int i = span.fromParagraph() + 1; i < span.toParagraph(); i++) {
				Paragraph middle = current.get(i);
				middle.restyle(0, middle.length(), change);
			}
			current.get(span.toParagraph()).restyle(0, span.toIndex(), change);
		}
		afterEdit();
	}

	/** Toggles a switch across the selection: on unless every character already has it. */
	public void toggle(java.util.function.Predicate<QuillStyle> reads, java.util.function.BiFunction<QuillStyle, Boolean, QuillStyle> writes) {
		boolean allOn = selectionAll(reads);
		restyle(style -> writes.apply(style, !allOn));
	}

	public boolean selectionAll(java.util.function.Predicate<QuillStyle> reads) {
		Span span = selection();
		if (span.isEmpty()) {
			return reads.test(activeStyle());
		}
		List<Paragraph> current = currentPage();
		boolean any = false;
		for (int p = span.fromParagraph(); p <= span.toParagraph(); p++) {
			Paragraph target = current.get(p);
			int from = p == span.fromParagraph() ? span.fromIndex() : 0;
			int to = p == span.toParagraph() ? span.toIndex() : target.length();
			for (int i = from; i < to; i++) {
				any = true;
				if (!reads.test(target.styleAt(i))) {
					return false;
				}
			}
		}
		return any;
	}

	public void setAlignment(Alignment alignment) {
		document.mark();
		Span span = selection();
		List<Paragraph> current = currentPage();
		for (int p = span.fromParagraph(); p <= span.toParagraph(); p++) {
			current.get(p).setAlignment(alignment);
		}
		afterEdit();
	}

	/** Sets every paragraph the selection touches to one of the ready-made looks. */
	public void setParagraphStyle(ParagraphStyle style) {
		document.mark();
		Span span = selection();
		List<Paragraph> current = currentPage();
		for (int p = span.fromParagraph(); p <= span.toParagraph(); p++) {
			style.applyTo(current.get(p));
		}
		// On an empty line there is nothing to restyle, so the style has to be handed to whatever
		// gets typed next. Without this, choosing a heading and then writing it gave plain text –
		// the style only worked on words that were already there, which is backwards.
		pending = current.get(paragraph).isEmpty()
				? style.runStyle(activeStyle())
				: null;
		afterEdit();
	}

	/** What the paragraph the caret is in looks like now, for showing which entry is the current one. */
	public ParagraphStyle paragraphStyle() {
		List<Paragraph> current = currentPage();
		return paragraph < current.size() ? ParagraphStyle.of(current.get(paragraph)) : ParagraphStyle.NORMAL;
	}

	public void setList(ListStyle list) {
		document.mark();
		Span span = selection();
		List<Paragraph> current = currentPage();
		for (int p = span.fromParagraph(); p <= span.toParagraph(); p++) {
			Paragraph target = current.get(p);
			target.setList(target.list() == list ? ListStyle.NONE : list);
		}
		afterEdit();
	}

	public void indent(int delta) {
		document.mark();
		Span span = selection();
		List<Paragraph> current = currentPage();
		for (int p = span.fromParagraph(); p <= span.toParagraph(); p++) {
			Paragraph target = current.get(p);
			target.setIndent(target.indent() + delta);
		}
		afterEdit();
	}

	/** Puts every character in the selection back to plain text, links and all. */
	public void clearFormatting() {
		restyle(style -> QuillStyle.PLAIN);
		Span span = selection();
		if (!span.isEmpty()) {
			List<Paragraph> current = currentPage();
			for (int p = span.fromParagraph(); p <= span.toParagraph(); p++) {
				current.get(p).setAlignment(Alignment.LEFT);
				current.get(p).setList(ListStyle.NONE);
				current.get(p).setIndent(0);
			}
			afterEdit();
		}
	}

	// ---- moving about ---------------------------------------------------------------------------------

	public void moveLeft(boolean extend, boolean wholeWord) {
		if (caret > 0) {
			caret = wholeWord ? wordStart(currentPage().get(paragraph), caret) : caret - 1;
		} else if (paragraph > 0) {
			paragraph--;
			caret = currentPage().get(paragraph).length();
		}
		if (!extend) {
			clearSelection();
		}
	}

	public void moveRight(boolean extend, boolean wholeWord) {
		Paragraph host = currentPage().get(paragraph);
		if (caret < host.length()) {
			caret = wholeWord ? wordEnd(host, caret) : caret + 1;
		} else if (paragraph + 1 < currentPage().size()) {
			paragraph++;
			caret = 0;
		}
		if (!extend) {
			clearSelection();
		}
	}

	public void moveVertically(int delta, boolean extend) {
		int index = lineIndexOfCaret();
		int target = index + delta;
		List<Layout.LaidLine> laid = lines();
		if (target < 0 || target >= laid.size()) {
			return;
		}
		Layout.LaidLine from = laid.get(index);
		float x = Layout.xOf(from, currentPage().get(from.paragraph), caret);
		Layout.LaidLine to = laid.get(target);
		paragraph = to.paragraph;
		caret = Layout.indexAt(to, currentPage().get(to.paragraph), x);
		if (!extend) {
			clearSelection();
		}
	}

	public void moveToLineStart(boolean extend) {
		Layout.LaidLine line = lines().get(lineIndexOfCaret());
		paragraph = line.paragraph;
		caret = line.start;
		if (!extend) {
			clearSelection();
		}
	}

	public void moveToLineEnd(boolean extend) {
		Layout.LaidLine line = lines().get(lineIndexOfCaret());
		paragraph = line.paragraph;
		caret = line.contentEnd;
		if (!extend) {
			clearSelection();
		}
	}

	public void selectAll() {
		List<Paragraph> current = currentPage();
		anchorParagraph = 0;
		anchorCaret = 0;
		paragraph = current.size() - 1;
		caret = current.get(paragraph).length();
	}

	public void selectWord() {
		Paragraph host = currentPage().get(paragraph);
		anchorParagraph = paragraph;
		anchorCaret = wordStart(host, Math.min(caret + 1, host.length()));
		caret = wordEnd(host, anchorCaret);
	}

	public void selectLine() {
		Layout.LaidLine line = lines().get(lineIndexOfCaret());
		anchorParagraph = line.paragraph;
		anchorCaret = line.start;
		paragraph = line.paragraph;
		caret = line.contentEnd;
	}

	/** Which laid-out line the caret is sitting on. */
	public int lineIndexOfCaret() {
		List<Layout.LaidLine> laid = lines();
		int fallback = 0;
		for (int i = 0; i < laid.size(); i++) {
			Layout.LaidLine line = laid.get(i);
			if (line.paragraph != paragraph) {
				continue;
			}
			fallback = i;
			if (caret <= line.end && (caret >= line.start || line.start == 0)) {
				if (caret < line.start) {
					continue;
				}
				// A caret exactly on a wrap point belongs at the start of the next line – but only
				// where the line broke at a space, which is the caret sitting after that space. A
				// line broken inside a word has nothing between the two halves, and sending the
				// caret to the next line there means clicking the last word on a line puts the
				// caret somewhere the click never pointed at.
				if (caret == line.end && line.end > line.contentEnd && !line.lastOfParagraph
						&& i + 1 < laid.size() && laid.get(i + 1).paragraph == paragraph) {
					continue;
				}
				return i;
			}
		}
		return fallback;
	}

	private static int wordStart(Paragraph host, int from) {
		int i = Math.max(0, Math.min(from, host.length()));
		while (i > 0 && host.charAt(i - 1) == ' ') {
			i--;
		}
		while (i > 0 && host.charAt(i - 1) != ' ') {
			i--;
		}
		return i;
	}

	private static int wordEnd(Paragraph host, int from) {
		int i = Math.max(0, Math.min(from, host.length()));
		while (i < host.length() && host.charAt(i) != ' ') {
			i++;
		}
		while (i < host.length() && host.charAt(i) == ' ') {
			i++;
		}
		return i;
	}

	// ---- the clipboard --------------------------------------------------------------------------------

	/** What the selection would be as plain characters, for the system clipboard. */
	public String selectedText(boolean withCodes) {
		Span span = selection();
		if (span.isEmpty()) {
			return "";
		}
		List<Paragraph> current = currentPage();
		StringBuilder out = new StringBuilder();
		for (int p = span.fromParagraph(); p <= span.toParagraph(); p++) {
			Paragraph target = current.get(p);
			int from = p == span.fromParagraph() ? span.fromIndex() : 0;
			int to = p == span.toParagraph() ? span.toIndex() : target.length();
			if (p > span.fromParagraph()) {
				out.append('\n');
			}
			if (withCodes) {
				Paragraph slice = target.slice(from, to);
				out.append(LegacyCodec.encode(List.of(slice), Layout.lay(List.of(slice), config.layoutOptions())));
			} else {
				out.append(target.text(), from, to);
			}
		}
		return out.toString();
	}

	public List<Paragraph> selectedParagraphs() {
		Span span = selection();
		List<Paragraph> current = currentPage();
		List<Paragraph> out = new ArrayList<>();
		if (span.isEmpty()) {
			return out;
		}
		for (int p = span.fromParagraph(); p <= span.toParagraph(); p++) {
			Paragraph target = current.get(p);
			int from = p == span.fromParagraph() ? span.fromIndex() : 0;
			int to = p == span.toParagraph() ? span.toIndex() : target.length();
			out.add(target.slice(from, to));
		}
		return out;
	}

	// ---- pages ------------------------------------------------------------------------------------------

	/**
	 * A new page at the end of the book, and goes to it.
	 *
	 * <p>Kept apart from inserting one here. Adding a page to a finished chapter and wedging one in
	 * between two written ones are different intentions, and a single button that does whichever
	 * depending on where you happen to be standing does the wrong one half the time.
	 */
	public void appendPage() {
		if (!document.canAddPage()) {
			return;
		}
		document.mark();
		document.insertPage(document.pageCount(), QuillDocument.newPage());
		setPage(document.pageCount() - 1);
		changed = true;
	}

	/**
	 * An empty page where this one is, pushing this one and everything after it along.
	 *
	 * <p>In front of the current page rather than behind it, because that is what the button is for:
	 * you are looking at the page you want to write something before. A page added after the one you
	 * are on is a page you then have to turn to, and one you cannot use to put a title in front of a
	 * chapter at all.
	 *
	 * <p>Only the button. Turning past the end of the book and asking for a new page while writing
	 * are the opposite movement and have their own – see {@link #newPageAfter()}. Sharing this one
	 * with them left the writer looking at a blank page with their own text moved on to the next.
	 */
	public void addPage() {
		if (!document.canAddPage()) {
			return;
		}
		document.mark();
		document.insertPage(page, QuillDocument.newPage());
		setPage(page);
		changed = true;
	}

	/**
	 * An empty page after this one, with the caret on it.
	 *
	 * <p>What asking for a new page while writing means: the page being written stays where it is
	 * and the writing carries on overleaf.
	 */
	public void newPageAfter() {
		if (!document.canAddPage()) {
			return;
		}
		document.mark();
		document.insertPage(page + 1, QuillDocument.newPage());
		setPage(page + 1);
		changed = true;
	}

	/**
	 * Sets the current page in two columns, spilling into new pages if it no longer fits on one.
	 *
	 * <p>A change to the text, not a way of showing it – see {@link Columns}. Undo is the way back.
	 */
	public void toTwoColumns() {
		List<List<Paragraph>> built = Columns.split(currentPage(), config.layoutOptions());
		if (built.isEmpty()) {
			return;
		}
		document.mark();
		document.pages().set(page, built.get(0));
		for (int i = 1; i < built.size() && document.canAddPage(); i++) {
			document.insertPage(page + i, built.get(i));
		}
		setCaret(0, 0, false);
		changed = true;
		invalidate();
	}

	/**
	 * Steps back, and goes to where the step was.
	 *
	 * <p>Undo works on the whole book, not on the page in front of you – a book is one document and
	 * a step back is a step back. But a change on page eleven undone while you are looking at page
	 * three is a change nobody sees, so the editor follows it: the first page that came out
	 * different is the page it shows, and the caret lands where the two versions part company, which
	 * is the end of what was just taken away.
	 */
	public void stepBack(boolean forward) {
		List<List<Paragraph>> before = snapshotText();
		if (forward) {
			document.redo();
		} else {
			document.undo();
		}
		setPage(Math.min(page, document.pageCount() - 1));
		followChange(before);
		changed = true;
		invalidate();
	}

	/** The text of every paragraph of every page, for telling afterwards what moved. */
	private List<List<Paragraph>> snapshotText() {
		List<List<Paragraph>> out = new ArrayList<>();
		for (List<Paragraph> current : document.pages()) {
			out.add(new ArrayList<>(current));
		}
		return out;
	}

	/** Finds the first place the book differs from how it was, and puts the caret there. */
	private void followChange(List<List<Paragraph>> before) {
		for (int p = 0; p < document.pageCount(); p++) {
			List<Paragraph> now = document.page(p);
			List<Paragraph> was = p < before.size() ? before.get(p) : List.of();
			for (int i = 0; i < now.size(); i++) {
				String text = now.get(i).text();
				String old = i < was.size() ? was.get(i).text() : null;
				if (old != null && old.equals(text)) {
					continue;
				}
				setPage(p);
				// Where the two versions stop agreeing: the end of the word that was taken back,
				// rather than the start of the line, which is nowhere in particular.
				setCaret(i, endOfChange(old, text), false);
				return;
			}
			if (now.size() != was.size()) {
				setPage(p);
				setCaret(Math.max(0, now.size() - 1), 0, false);
				return;
			}
		}
	}

	/**
	 * Where the caret belongs after a step back: at the end of whatever changed.
	 *
	 * <p>Worked out from both ends. What the two versions share at the front is the text before the
	 * change; what they share at the back is the text after it. Everything between is the change,
	 * and the caret goes at its far end – so taking back a typed word leaves the caret at the end of
	 * the word before it, and putting a deleted word back leaves the caret at the end of the word
	 * that has just returned. Measuring from the front alone gave the start of it both times, which
	 * for the returned word is the wrong end entirely.
	 */
	private static int endOfChange(@Nullable String old, String now) {
		if (old == null) {
			return now.length();
		}
		int prefix = 0;
		int limit = Math.min(old.length(), now.length());
		while (prefix < limit && old.charAt(prefix) == now.charAt(prefix)) {
			prefix++;
		}
		int suffix = 0;
		while (suffix < limit - prefix
				&& old.charAt(old.length() - 1 - suffix) == now.charAt(now.length() - 1 - suffix)) {
			suffix++;
		}
		int end = Math.max(prefix, now.length() - suffix);
		// Back over a blank the change left trailing, so the caret sits at the end of a word rather
		// than at the start of the gap after it.
		while (end > 0 && end <= now.length() && Character.isWhitespace(now.charAt(end - 1))) {
			end--;
		}
		return Math.min(end, now.length());
	}

	/** Puts a frame of box drawing round the current page, laying its text into the room left. */
	public void frameCurrentPage(com.glamardor.roleplayersquill.text.FrameStyle style) {
		List<Paragraph> framed = Ornaments.frame(currentPage(), style);
		if (framed.isEmpty()) {
			return;
		}
		document.mark();
		document.pages().set(page, new ArrayList<>(framed));
		setCaret(0, 0, false);
		changed = true;
		invalidate();
	}

	/**
	 * Drops an ornament in on a line of its own.
	 *
	 * <p>Not through the ordinary paste, which folds the first paragraph it is given into the one the
	 * caret is in – and an ornament folded into a paragraph loses the centring that is most of what
	 * makes it an ornament. So it goes in whole, with an empty line after it to carry on writing on.
	 */
	public void insertOrnament(Paragraph ornament) {
		document.mark();
		List<Paragraph> current = currentPage();
		Paragraph here = current.get(paragraph);
		int at = here.isEmpty() ? paragraph : paragraph + 1;
		if (here.isEmpty()) {
			current.remove(paragraph);
		}
		current.add(at, ornament);
		current.add(at + 1, new Paragraph());
		setCaret(at + 1, 0, false);
		changed = true;
		invalidate();
	}

	/** Puts a paragraph at the end of the page, which is where a footnote and its rule go. */
	public void appendParagraph(Paragraph paragraph) {
		document.mark();
		currentPage().add(paragraph);
		changed = true;
		invalidate();
	}

	/**
	 * The paragraphs the selection covers, or the one the caret is in when nothing is selected.
	 *
	 * <p>What a set of formatting is kept from: a signature is three lines somebody has already set
	 * out, and pointing at them is how you say which three.
	 */
	public List<Paragraph> paragraphsForSet() {
		List<Paragraph> selected = selectedParagraphs();
		return selected.isEmpty() ? List.of(currentPage().get(paragraph).copy()) : selected;
	}

	/**
	 * Drops a few ready-made paragraphs in where the caret is.
	 *
	 * <p>What a set of formatting is for: a signature, a dateline, the three lines a decree opens
	 * with. Unlike a template it is not a page – it goes into the page being written, after the
	 * paragraph the caret is standing in, and the caret follows it so the writing carries on below.
	 */
	public void insertSet(List<Paragraph> set) {
		if (set.isEmpty()) {
			return;
		}
		document.mark();
		deleteSelectionQuietly();
		List<Paragraph> current = currentPage();
		int at = paragraph;
		// After the paragraph the caret is in, unless that paragraph is empty – an empty line is
		// where somebody meant the thing to go, not something to be pushed down by it.
		if (!current.get(at).isEmpty()) {
			at++;
		} else {
			current.remove(at);
		}
		for (int i = 0; i < set.size(); i++) {
			current.add(Math.min(at + i, current.size()), set.get(i).copy());
		}
		int last = Math.min(at + set.size() - 1, current.size() - 1);
		setCaret(last, current.get(last).length(), false);
		changed = true;
		invalidate();
	}

	/** A ready-made page directly after this one, and the caret moves onto it. */
	public void insertPageAfter(List<Paragraph> page) {
		if (!document.canAddPage()) {
			return;
		}
		document.mark();
		document.insertPage(this.page + 1, new ArrayList<>(page));
		setPage(this.page + 1);
		setCaret(0, 0, false);
		changed = true;
	}

	/**
	 * Takes out the contents pages the book already has.
	 *
	 * <p>Which is the page headed with the contents' own title, and every page straight after it
	 * whose lines are all entries – a name, a leader and a number. Stopping at the first page that
	 * is not one of those means a book whose contents ran to three pages loses all three, and a book
	 * that happens to start with something else loses nothing.
	 */
	public void dropContents(String title) {
		if (document.pageCount() == 0) {
			return;
		}
		List<Paragraph> first = document.page(0);
		if (first.isEmpty() || !first.get(0).text().trim().equals(title)) {
			return;
		}
		document.mark();
		int pages = 1;
		while (pages < document.pageCount() && allEntries(document.page(pages))) {
			pages++;
		}
		for (int i = 0; i < pages && document.pageCount() > 1; i++) {
			document.removePage(0);
		}
		setPage(0);
		setCaret(0, 0, false);
		changed = true;
		invalidate();
	}

	/** Whether every line on a page is a contents entry, which is text, a leader and a number. */
	private static boolean allEntries(List<Paragraph> page) {
		boolean any = false;
		for (Paragraph paragraph : page) {
			if (paragraph.isEmpty()) {
				continue;
			}
			if (paragraph.text().indexOf(Widths.LEADER) < 0) {
				return false;
			}
			any = true;
		}
		return any;
	}

	/** Pages at the very front, which is where a contents page goes. */
	public void insertPagesAtFront(List<List<Paragraph>> incoming) {
		if (incoming.isEmpty() || document.pageCount() + incoming.size() > QuillDocument.MAX_PAGES) {
			return;
		}
		document.mark();
		for (int i = incoming.size() - 1; i >= 0; i--) {
			document.insertPage(0, new ArrayList<>(incoming.get(i)));
		}
		setPage(0);
		setCaret(0, 0, false);
		changed = true;
	}

	/** The options the page is laid out with, for the windows that show a page of their own. */
	public Layout.Options layoutOptions() {
		return config.layoutOptions();
	}

	public void removePage() {
		document.mark();
		document.removePage(page);
		setPage(Math.min(page, document.pageCount() - 1));
		changed = true;
	}

	public void clearCurrentPage() {
		document.mark();
		document.clearPage(page);
		setPage(page);
		changed = true;
	}

	public void duplicatePage() {
		if (!document.canAddPage()) {
			return;
		}
		document.mark();
		document.insertPage(page + 1, QuillDocument.copyPage(currentPage()));
		setPage(page + 1);
		changed = true;
	}

	public void movePage(int delta) {
		int to = page + delta;
		if (to < 0 || to >= document.pageCount()) {
			return;
		}
		document.mark();
		document.movePage(page, to);
		setPage(to);
		changed = true;
	}

	// ---- pouring text through the pages -------------------------------------------------------------------

	/**
	 * Whether this page has outgrown what a book page can hold.
	 *
	 * <p>Both limits count: fourteen lines of room, and a thousand-odd characters once the codes and
	 * the pad spaces are written out.
	 */
	public boolean overflows() {
		if (contentLines() > Layout.PAGE_LINES) {
			return true;
		}
		return LegacyCodec.encode(currentPage(), lines()).length() > QuillDocument.MAX_PAGE_CHARS;
	}

	/**
	 * The first page of the book that will not survive being written out, or -1 if none will not.
	 *
	 * <p>The warning under the page only ever speaks for the page being looked at, and a book is
	 * signed from whichever page the writer happened to stop on. A page that has outgrown its room
	 * three chapters back is one the server either refuses outright – a page over the character
	 * limit fails the component's own check and the whole edit is thrown away – or accepts and draws
	 * short, which is the reader losing the bottom of a page and nobody being told. Neither is
	 * something to find out about after signing.
	 *
	 * <p>Pages nobody edited are not measured. They arrived from the server, which means the server
	 * already took them, and they are going back out as the very strings they came in as.
	 */
	public int firstPageThatOverflows() {
		List<List<Paragraph>> pages = document.pages();
		for (int i = 0; i < pages.size(); i++) {
			String going = pageString(pages.get(i));
			if (going.length() > QuillDocument.MAX_PAGE_CHARS
					|| LegacyCodec.editorLines(going) > Layout.PAGE_LINES) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * How many lines the page really uses, not counting blank ones trailing off the end.
	 *
	 * <p>A page that ends in an empty paragraph is not a page that has run out of room: nothing is
	 * drawn there and nothing is lost. Counting those was the difference between a page that says it
	 * overflows and a page that does.
	 */
	public int contentLines() {
		return contentLines(lines());
	}

	private static int contentLines(List<Layout.LaidLine> laid) {
		int last = laid.size();
		while (last > 0) {
			Layout.LaidLine line = laid.get(last - 1);
			if (line.contentEnd > line.start || !line.marker.isEmpty()) {
				break;
			}
			last--;
		}
		return last;
	}

	/** The first line that will not be shown, as plain text, or empty when everything fits. */
	public String firstLineCutOff() {
		List<Layout.LaidLine> laid = lines();
		for (int i = Layout.PAGE_LINES; i < laid.size(); i++) {
			Layout.LaidLine line = laid.get(i);
			if (line.contentEnd > line.start) {
				return currentPage().get(line.paragraph).text().substring(line.start, line.contentEnd);
			}
		}
		return "";
	}

	/** How much of the page's thousand characters is spent. */
	public int cost() {
		return LegacyCodec.encode(currentPage(), lines()).length();
	}

	/**
	 * Re-pours this page and every page after it, so that text pushed off the bottom of one lands at
	 * the top of the next instead of being refused.
	 *
	 * <p>The caret travels with the character it was in front of, which is the only behaviour that
	 * does not feel like the editor losing your place.
	 */
	public void reflow() {
		int marker = absoluteCaret();
		int last = lastPageToTouch();
		List<Paragraph> flow = flowFrom(page, last);
		List<List<Paragraph>> repaginated = Paginator.paginate(flow, config.paginatorOptions());
		applyFlow(page, last, repaginated);
		restoreCaret(marker);
		invalidate();
		changed = true;
	}

	/**
	 * How far the repour has to reach.
	 *
	 * <p>As far as the first page with room to take what is pushed into it, and no further. Without
	 * this, typing at the bottom of page two of a fifty page book relays all forty-nine of the
	 * others on every keystroke – correct, and a stutter.
	 */
	private int lastPageToTouch() {
		int last = page;
		while (last < document.pageCount() - 1) {
			if (hasRoom(document.page(last)) && !continuesInto(last + 1)) {
				break;
			}
			last++;
		}
		return last;
	}

	/** Whether a page could take one more line without overflowing either limit. */
	private boolean hasRoom(List<Paragraph> target) {
		List<Layout.LaidLine> laid = Layout.lay(target, config.layoutOptions());
		if (laid.size() >= Layout.PAGE_LINES) {
			return false;
		}
		// A line of text is a hundred-odd characters at the outside, plus the codes around it.
		return LegacyCodec.encode(target, laid).length() < QuillDocument.MAX_PAGE_CHARS - 160;
	}

	/** True when the next page opens with the tail of a paragraph this one started. */
	private boolean continuesInto(int next) {
		List<Paragraph> target = document.page(next);
		return !target.isEmpty() && target.get(0).isContinuation();
	}

	/** Joins a run of pages back into one stream of paragraphs. */
	private List<Paragraph> flowFrom(int first, int last) {
		List<Paragraph> flow = new ArrayList<>();
		for (int p = first; p <= last && p < document.pageCount(); p++) {
			List<Paragraph> current = document.page(p);
			for (int i = 0; i < current.size(); i++) {
				Paragraph paragraphCopy = current.get(i).copy();
				boolean joins = i == 0 && paragraphCopy.isContinuation() && !flow.isEmpty();
				paragraphCopy.setContinuation(false);
				paragraphCopy.setContinues(false);
				if (joins) {
					Paragraph previous = flow.get(flow.size() - 1);
					previous.setContinues(false);
					previous.insert(previous.length(), paragraphCopy.text(),
							paragraphCopy.stylesIn(0, paragraphCopy.length()));
				} else {
					flow.add(paragraphCopy);
				}
			}
		}
		return flow;
	}

	/** Puts the repoured pages back in place of the ones they came from, leaving the rest alone. */
	private void applyFlow(int first, int last, List<List<Paragraph>> pages) {
		List<List<Paragraph>> all = document.pages();
		int end = Math.min(last, all.size() - 1);
		for (int p = end; p >= first; p--) {
			all.remove(p);
		}
		int at = first;
		for (List<Paragraph> laid : pages) {
			if (all.size() >= QuillDocument.MAX_PAGES) {
				break;
			}
			all.add(at++, laid);
		}
		if (all.isEmpty()) {
			all.add(QuillDocument.newPage());
		}
		page = Math.min(page, all.size() - 1);
	}

	/**
	 * Where the caret is, counted from the start of the page it is on, with a paragraph break
	 * costing one character – the same arithmetic the flow uses, so the number survives a repour.
	 */
	private int absoluteCaret() {
		int total = 0;
		List<Paragraph> current = currentPage();
		for (int i = 0; i < paragraph && i < current.size(); i++) {
			total += current.get(i).length() + 1;
		}
		return total + caret;
	}

	private void restoreCaret(int marker) {
		int remaining = marker;
		for (int p = page; p < document.pageCount(); p++) {
			List<Paragraph> current = document.page(p);
			int total = 0;
			for (Paragraph target : current) {
				total += target.length() + 1;
			}
			if (remaining < total || p == document.pageCount() - 1) {
				page = p;
				for (int i = 0; i < current.size(); i++) {
					int length = current.get(i).length();
					if (remaining <= length) {
						paragraph = i;
						caret = Math.max(0, Math.min(remaining, length));
						clearSelection();
						return;
					}
					remaining -= length + 1;
				}
				paragraph = current.size() - 1;
				caret = current.get(paragraph).length();
				clearSelection();
				return;
			}
			remaining -= total;
		}
	}

	private void afterEdit() {
		invalidate();
		changed = true;
		if (overflows()) {
			reflow();
		}
	}

	// ---- writing the book out ------------------------------------------------------------------------------

	/**
	 * Every page as the string a server will take.
	 *
	 * <p>A page still holding exactly what it was opened holding goes back out as the string it came
	 * in as. Only a page somebody edited is laid out and written again: re-typesetting the rest would
	 * quietly rewrite a book this mod did not write, which for a book lent by somebody else is their
	 * text coming back changed.
	 */
	public List<String> encodePages() {
		List<String> out = new ArrayList<>(document.pageCount());
		for (List<Paragraph> current : document.pages()) {
			out.add(pageString(current));
		}
		return out;
	}

	/**
	 * The string one page will go out as.
	 *
	 * <p>The one it came in as, where it came in as one and nobody has touched it. The exception is
	 * a page that does not fit the vanilla editor as it stands: leaving that alone would be leaving
	 * it broken for every reader without this mod, for the sake of not touching what this mod itself
	 * wrote badly. Laying it out again is what mends it, and it is the only thing that mends it
	 * without the writer retyping the book.
	 */
	private String pageString(List<Paragraph> current) {
		String received = document.sourceOf(current);
		if (received != null && LegacyCodec.fitsTheVanillaEditor(received)) {
			return received;
		}
		return LegacyCodec.encode(current, Layout.lay(current, config.layoutOptions()));
	}

	/** Every page as a component, for the creative road. */
	public List<net.minecraft.text.Text> encodeRichPages() {
		List<net.minecraft.text.Text> out = new ArrayList<>(document.pageCount());
		for (List<Paragraph> current : document.pages()) {
			out.add(com.glamardor.roleplayersquill.book.RichWriter.encode(
					current, Layout.lay(current, config.layoutOptions())));
		}
		return out;
	}

	/** True when anything on any page needs more than {@code §} codes to say what it means. */
	public boolean needsRich() {
		for (List<Paragraph> current : document.pages()) {
			for (Paragraph target : current) {
				for (int i = 0; i < target.length(); i++) {
					QuillStyle style = target.styleAt(i);
					if (style.hasInteraction()) {
						return true;
					}
					if (style.color() != QuillStyle.INHERIT
							&& style.color() != style.legacyPart().color()) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
