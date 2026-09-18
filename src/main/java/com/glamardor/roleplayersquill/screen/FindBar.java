package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.BookSearch;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The strip a browser calls a find bar: a box, two arrows and a cross.
 *
 * <p>Finding is nearly always what search is opened for, and the window that does it properly was
 * standing over the page it was searching. This stands under the book, out of the way, and works as
 * the word is typed: every keystroke moves the caret to the next match, so the page behind shows the
 * word rather than a message about it.
 *
 * <p>Replacing is not here. It is a different act – it changes the book – and it has the window it
 * always had, one button away.
 */
public class FindBar {
	private static final int HEIGHT = 18;
	private static final int WIDTH = 320;
	/** The arrows, the way to the replacing window and the cross, with the gaps between them. */
	private static final int BUTTONS = 148;
	/** The room kept for "3 of 11", which is never wider than this and never sits over the box. */
	private static final int TALLY = 42;

	private final PageEditor editor;
	private final Runnable openFull;
	private final Runnable onClose;

	private TextFieldWidget box;
	private int x;
	private int y;

	/** Where the last match was, so that the arrows walk rather than start over. */
	private BookSearch.Hit at = new BookSearch.Hit(0, 0, 0, 0);
	private int found;
	private int total;

	public FindBar(PageEditor editor, Runnable openFull, Runnable onClose) {
		this.editor = editor;
		this.openFull = openFull;
		this.onClose = onClose;
	}

	/** The room this takes, so the screen above it knows what is left. */
	public static int height() {
		return HEIGHT;
	}

	public void layout(int centreX, int top, int screenWidth, TextRenderer textRenderer,
			Consumer<ClickableWidget> add) {
		int width = Math.min(WIDTH, screenWidth - 8);
		this.x = centreX - width / 2;
		this.y = top;

		String was = box == null ? "" : box.getText();
		// The box stops short of the tally rather than sharing the room with it. Drawing "3/11" over
		// the end of the box put it on top of whatever had been typed, and a long word and a short
		// answer sat in the same pixels.
		int boxWidth = width - BUTTONS - TALLY;
		box = new TextFieldWidget(textRenderer, x + 2, y, boxWidth, HEIGHT,
				Text.translatable("roleplayersquill.find.needle"));
		box.setMaxLength(120);
		box.setPlaceholder(Text.translatable("roleplayersquill.find.needle").formatted(Formatting.DARK_GRAY));
		box.setText(was);
		// As it is typed, not when a button is pressed: the whole point of the strip is that the
		// page behind it keeps up.
		box.setChangedListener(value -> {
			at = new BookSearch.Hit(0, 0, 0, 0);
			step(true);
		});
		add.accept(box);

		int right = x + boxWidth + TALLY + 4;
		add.accept(ButtonWidget.builder(Text.literal("▲"), b -> step(false))
				.dimensions(right, y, 18, HEIGHT).build());
		add.accept(ButtonWidget.builder(Text.literal("▼"), b -> step(true))
				.dimensions(right + 20, y, 18, HEIGHT).build());
		add.accept(ButtonWidget.builder(Text.translatable("roleplayersquill.find.replaceOpen"),
						b -> openFull.run())
				.dimensions(right + 42, y, 80, HEIGHT).build());
		add.accept(ButtonWidget.builder(Text.literal("✕"), b -> onClose.run())
				.dimensions(right + 126, y, 18, HEIGHT).build());
	}

	/** The box, so the screen can hand it the keyboard when the strip opens. */
	@Nullable
	public TextFieldWidget box() {
		return box;
	}

	public String text() {
		return box == null ? "" : box.getText();
	}

	/** To the next match, or to the one before it. Nothing is announced: the caret is the answer. */
	public void step(boolean forwards) {
		String what = text();
		if (what.isEmpty()) {
			found = 0;
			total = 0;
			return;
		}
		List<List<com.glamardor.roleplayersquill.text.Paragraph>> pages = editor.document().pages();
		total = BookSearch.count(pages, what, false);
		BookSearch.Hit hit = forwards
				? BookSearch.next(pages, what, false, at)
				: BookSearch.previous(pages, what, false, at);
		if (hit == null) {
			found = 0;
			return;
		}
		at = hit;
		found = BookSearch.ordinalOf(pages, what, false, hit);
		editor.setPage(hit.page());
		editor.setCaret(hit.paragraph(), hit.from(), false);
		editor.setCaret(hit.paragraph(), hit.to(), true);
	}

	/** The strip itself, drawn under the widgets that sit on it. */
	public void renderBehind(DrawContext context) {
		if (box == null) {
			return;
		}
		int width = Math.min(WIDTH, context.getScaledWindowWidth() - 8);
		context.fill(x - 2, y - 3, x + width + 2, y + HEIGHT + 3, 0xD0101010);
		context.drawBorder(x - 2, y - 3, width + 4, HEIGHT + 6, 0xFF3A3A3A);
	}

	/**
	 * How many matches there are and which one the caret is on, in a slot of its own beside the box.
	 *
	 * <p>Always the same shape – a number, a slash and a number – so it fits the room kept for it
	 * whatever was typed. Nothing found is 0/0 in red, which is what every find bar says and is
	 * shorter than saying it in words.
	 */
	public void renderTally(DrawContext context, TextRenderer textRenderer) {
		if (box == null || text().isEmpty()) {
			return;
		}
		Text tally = total == 0
				? Text.literal("0/0").formatted(Formatting.RED)
				: Text.literal(found + "/" + total).formatted(Formatting.GRAY);
		int slot = box.getX() + box.getWidth();
		context.drawText(textRenderer, tally,
				slot + (TALLY - textRenderer.getWidth(tally)) / 2, y + 5, 0xFFFFFFFF, false);
	}
}
