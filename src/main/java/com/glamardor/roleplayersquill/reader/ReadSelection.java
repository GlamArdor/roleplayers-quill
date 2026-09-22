package com.glamardor.roleplayersquill.reader;

import com.glamardor.roleplayersquill.text.Layout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Selecting text over a page the game has already wrapped, and finding the same run of text
 * {@code FindBar} last moved to among those very lines.
 *
 * <p>Both read {@link PageText}, never the book's own paragraphs. A paragraph this mod's own
 * writer left to the game to wrap has no line breaks of its own to agree with, and a page written
 * by anything else has no paragraphs this mod recognises the shape of at all – but what is on the
 * screen, wrapped exactly as the screen wrapped it, is the one thing every page agrees with.
 */
public final class ReadSelection {
	private static final int COLOR = 0x663C6390;
	private static final int MATCH_COLOR = 0x80C99B3C;

	@Nullable
	private Integer anchorLine;
	private int anchorColumn;
	@Nullable
	private Integer cursorLine;
	private int cursorColumn;
	private boolean dragging;
	/**
	 * Whether the button now held was pressed on the page at all.
	 *
	 * <p>A press lands here from the screen's own {@code mouseClicked}, which fires wherever it was
	 * clicked – a side button, the Done button, the world showing past the edge of the book. Only a
	 * press that began on the text may go on to drag out a selection; without this, holding the
	 * button after pressing Contents and moving the pointer swept a selection across the page
	 * behind the panel.
	 */
	private boolean armed;

	public void clear() {
		anchorLine = null;
		cursorLine = null;
		dragging = false;
		armed = false;
	}

	public boolean hasSelection() {
		return anchorLine != null && cursorLine != null
				&& (!anchorLine.equals(cursorLine) || anchorColumn != cursorColumn);
	}

	/** Where the drag started. Not yet a selection – a click that never moves clears it again. */
	public boolean press(PageText page, TextRenderer renderer, int textX, int textY, double mouseX, double mouseY) {
		if (!onText(page, textX, textY, mouseX, mouseY)) {
			armed = false;
			return false;
		}
		int[] at = locate(page, renderer, textX, textY, mouseX, mouseY);
		if (at == null) {
			armed = false;
			return false;
		}
		anchorLine = at[0];
		anchorColumn = at[1];
		cursorLine = at[0];
		cursorColumn = at[1];
		dragging = false;
		armed = true;
		return true;
	}

	/**
	 * Carries the selection out to where the pointer is now.
	 *
	 * <p>Deliberately not bounded to the page the way a press is: sweeping out past the margin and
	 * on down is how anybody selects to the end of a paragraph, and the line the pointer is level
	 * with is the answer whether it is over the text or beside it.
	 */
	public void drag(PageText page, TextRenderer renderer, int textX, int textY, double mouseX, double mouseY) {
		if (!armed || anchorLine == null) {
			return;
		}
		int line = lineAt(page, textY, mouseY);
		int localX = (int) Math.round(mouseX - textX);
		cursorLine = line;
		cursorColumn = page.columnAt(renderer, line, Math.max(0, localX));
		dragging = true;
	}

	/** A press that never became a drag leaves nothing selected, the way a plain click should. */
	public void release() {
		armed = false;
		if (!dragging) {
			clear();
		}
	}

	/**
	 * A double click: the word under the pointer, by the same rule the editor selects one with –
	 * a run of anything that is not a blank, with the blanks after it taken too, so retyping over
	 * it never leaves two spaces behind.
	 *
	 * <p>Marked {@code dragging} straight away. Otherwise the very next frame, still with the
	 * button held from the click that made this selection, would run an ordinary drag to wherever
	 * the pointer already is – which is some point inside the word just selected – and narrow the
	 * selection back down to nothing worth having doubled clicked for.
	 */
	public boolean selectWord(PageText page, TextRenderer renderer, int textX, int textY, double mouseX, double mouseY) {
		if (!onText(page, textX, textY, mouseX, mouseY)) {
			return false;
		}
		int[] at = locate(page, renderer, textX, textY, mouseX, mouseY);
		if (at == null) {
			return false;
		}
		String text = page.line(at[0]).text();
		anchorLine = at[0];
		anchorColumn = wordStart(text, Math.min(at[1] + 1, text.length()));
		cursorLine = at[0];
		cursorColumn = wordEnd(text, anchorColumn);
		dragging = true;
		return true;
	}

	/** A triple click: the whole line the pointer landed on. */
	public boolean selectLine(PageText page, TextRenderer renderer, int textX, int textY, double mouseX, double mouseY) {
		if (!onText(page, textX, textY, mouseX, mouseY)) {
			return false;
		}
		int[] at = locate(page, renderer, textX, textY, mouseX, mouseY);
		if (at == null) {
			return false;
		}
		anchorLine = at[0];
		anchorColumn = 0;
		cursorLine = at[0];
		cursorColumn = page.line(at[0]).length();
		dragging = true;
		return true;
	}

	/** Where a word starts, the same rule {@code PageEditor} uses: back past the run, and the blank before it. */
	private static int wordStart(String text, int from) {
		int i = Math.max(0, Math.min(from, text.length()));
		while (i > 0 && text.charAt(i - 1) == ' ') {
			i--;
		}
		while (i > 0 && text.charAt(i - 1) != ' ') {
			i--;
		}
		return i;
	}

	private static int wordEnd(String text, int from) {
		int i = Math.max(0, Math.min(from, text.length()));
		while (i < text.length() && text.charAt(i) != ' ') {
			i++;
		}
		while (i < text.length() && text.charAt(i) == ' ') {
			i++;
		}
		return i;
	}

	@Nullable
	private int[] locate(PageText page, TextRenderer renderer, int textX, int textY, double mouseX, double mouseY) {
		int line = (int) Math.floor((mouseY - textY) / (double) Layout.LINE_HEIGHT);
		if (line < 0 || line >= page.lineCount()) {
			return null;
		}
		int localX = (int) Math.round(mouseX - textX);
		int column = page.columnAt(renderer, line, Math.max(0, localX));
		return new int[] { line, column };
	}

	/** Whether a point is over the written part of the page, rather than the margin or a button. */
	private static boolean onText(PageText page, int textX, int textY, double mouseX, double mouseY) {
		if (page.lineCount() == 0) {
			return false;
		}
		return mouseX >= textX - 2 && mouseX <= textX + Layout.PAGE_WIDTH + 2
				&& mouseY >= textY && mouseY < textY + page.lineCount() * Layout.LINE_HEIGHT;
	}

	/** The line a point is level with, clamped to the page – for a drag that has left the margin. */
	private static int lineAt(PageText page, int textY, double mouseY) {
		int line = (int) Math.floor((mouseY - textY) / (double) Layout.LINE_HEIGHT);
		return Math.max(0, Math.min(line, page.lineCount() - 1));
	}

	public void selectAll(PageText page) {
		if (page.lineCount() == 0) {
			clear();
			return;
		}
		anchorLine = 0;
		anchorColumn = 0;
		int last = page.lineCount() - 1;
		cursorLine = last;
		cursorColumn = page.line(last).length();
		dragging = true;
		// Not a gesture, so no button is waiting to carry it anywhere: a drag polled while the
		// key was pressed would otherwise pull the whole-page selection back to the pointer.
		armed = false;
	}

	@Nullable
	private String selectedText(PageText page) {
		if (!hasSelection()) {
			return null;
		}
		int[] o = ordered();
		return page.textBetween(o[0], o[1], o[2], o[3]);
	}

	public void copy(MinecraftClient client, PageText page) {
		String text = selectedText(page);
		if (text != null && !text.isEmpty()) {
			client.keyboard.setClipboard(text);
		}
	}

	private int[] ordered() {
		int anchor = anchorLine;
		int cursor = cursorLine;
		if (anchor < cursor || anchor == cursor && anchorColumn <= cursorColumn) {
			return new int[] { anchor, anchorColumn, cursor, cursorColumn };
		}
		return new int[] { cursor, cursorColumn, anchor, anchorColumn };
	}

	public void renderSelection(DrawContext context, PageText page, TextRenderer renderer, int textX, int textY) {
		if (!hasSelection()) {
			return;
		}
		int[] o = ordered();
		paint(context, page, renderer, textX, textY, o[0], o[1], o[2], o[3], COLOR);
	}

	/** The run {@code FindBar} last moved to, drawn even where a hyphen broke it across a line. */
	public void renderMatch(DrawContext context, PageText page, TextRenderer renderer, int textX, int textY,
			@Nullable String needle, int ordinal) {
		for (int[] span : locateMatch(page, needle, ordinal)) {
			paint(context, page, renderer, textX, textY, span[0], span[1], span[0], span[2], MATCH_COLOR);
		}
	}

	private static void paint(DrawContext context, PageText page, TextRenderer renderer, int textX, int textY,
			int fromLine, int fromColumn, int toLine, int toColumn, int color) {
		for (int line = fromLine; line <= toLine; line++) {
			int from = line == fromLine ? fromColumn : 0;
			int to = line == toLine ? toColumn : page.line(line).length();
			if (from >= to) {
				continue;
			}
			int left = textX + page.widthTo(renderer, line, from);
			int right = textX + page.widthTo(renderer, line, to);
			int top = textY + line * Layout.LINE_HEIGHT;
			context.fill(left, top, right, top + Layout.LINE_HEIGHT, color);
		}
	}

	/**
	 * Where {@code needle}'s {@code ordinal}-th occurrence on this page sits, as one span per line
	 * it runs across.
	 *
	 * <p>Not a search over the book's paragraphs – this is a second, independent search, over the
	 * very lines the screen wrapped the page into, for the same text. The two only ever have to
	 * agree on how many times a word occurs on one page, and on the order they occur in, which
	 * holds because both walk the page top to bottom; they never have to agree on a character
	 * offset, which is the one thing they could not be made to agree on without also reproducing
	 * this mod's own alignment arithmetic here for every page, including ones this mod never wrote.
	 */
	static List<int[]> locateMatch(PageText page, @Nullable String needle, int ordinal) {
		List<int[]> spans = new ArrayList<>();
		if (needle == null || needle.isEmpty() || ordinal <= 0 || page.lineCount() == 0) {
			return spans;
		}
		StringBuilder joined = new StringBuilder();
		int[] lineStart = new int[page.lineCount()];
		for (int i = 0; i < page.lineCount(); i++) {
			if (i > 0 && !glued(page.line(i - 1).text())) {
				joined.append(' ');
			}
			lineStart[i] = joined.length();
			joined.append(page.line(i).text());
		}

		String hay = joined.toString().toLowerCase(Locale.ROOT);
		String want = needle.toLowerCase(Locale.ROOT);
		int at = -1;
		for (int n = 0; n < ordinal; n++) {
			at = hay.indexOf(want, at + 1);
			if (at < 0) {
				return spans;
			}
		}
		int end = at + want.length();

		int startLine = lineOf(lineStart, at);
		int endLine = lineOf(lineStart, Math.max(at, end - 1));
		for (int line = startLine; line <= endLine; line++) {
			int lineEnd = lineStart[line] + page.line(line).length();
			int from = Math.max(at, lineStart[line]) - lineStart[line];
			int to = Math.min(end, lineEnd) - lineStart[line];
			from = Math.max(0, Math.min(from, page.line(line).length()));
			to = Math.max(0, Math.min(to, page.line(line).length()));
			if (from < to) {
				spans.add(new int[] { line, from, to });
			}
		}
		return spans;
	}

	/** Whether a line ending like this carries straight on into the next with no space – a hyphen. */
	private static boolean glued(String previousLine) {
		int length = previousLine.length();
		return length >= 2 && previousLine.charAt(length - 1) == '-'
				&& Character.isLetter(previousLine.charAt(length - 2));
	}

	private static int lineOf(int[] lineStart, int position) {
		int line = 0;
		for (int i = 0; i < lineStart.length; i++) {
			if (lineStart[i] <= position) {
				line = i;
			}
		}
		return line;
	}
}
