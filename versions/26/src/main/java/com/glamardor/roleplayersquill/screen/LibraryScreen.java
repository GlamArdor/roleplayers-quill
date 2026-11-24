package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;

import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.text.BookSearch;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.Paragraph;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Finding a word in every book this computer remembers, not just the one in hand.
 *
 * <p>A draft is kept for every book that has ever been opened here, so between them they are the
 * writer's library. The question this answers is the one that cannot be answered by holding a book:
 * which book was it that had the list of chapters in it.
 *
 * <p>It cannot open the book it finds – that book is an item somewhere in the world, and this is a
 * client. What it can do is show the page and hand it over: copy it, and it can be pasted into
 * whatever is being written now.
 *
 * <p>The page stands beside the list, laid out the way the book lays it out, with the words that
 * were looked for marked on it. A line of text lifted out of a page says what was found; the page
 * says which one of the four books that all mention the harbour this is.
 */
public class LibraryScreen extends DialogScreen {
	private static final int ROW = 26;
	private static final int SHOWN = 6;
	/** How wide the list of pages is; the page itself stands to the right of it. */
	private static final int LIST = 196;
	/** The parchment a found word is marked on. */
	private static final int MARK = 0x80E8B23A;

	private record Found(String book, long when, int page, String line, List<Paragraph> paragraphs,
			BookSearch.Hit hit) {
	}

	private final List<BookIO.Kept> books;
	private final List<Found> found = new ArrayList<>();
	private EditBox needle;
	private int scroll;
	private int chosen = -1;
	@Nullable
	private Component notice;

	public LibraryScreen(@Nullable Screen parent, String initial) {
		super(parent, Component.translatable("roleplayersquill.library.title"));
		this.panelWidth = LIST + (int) Layout.PAGE_WIDTH + 48;
		this.panelHeight = SHOWN * ROW + 104;
		this.books = BookIO.allBooks();
		this.initial = initial;
	}

	private final String initial;

	@Override
	protected void init() {
		super.init();
		String was = needle == null ? initial : needle.getValue();
		needle = new EditBox(font, panelX + 12, panelY + 24, LIST - 4, 18,
				Component.translatable("roleplayersquill.find.needle"));
		needle.setMaxLength(120);
		needle.setHint(Component.translatable("roleplayersquill.find.needle").withStyle(ChatFormatting.DARK_GRAY));
		needle.setValue(was);
		needle.setResponder(value -> search(value));
		addRenderableWidget(needle);
		setInitialFocus(needle);

		int y = panelY + panelHeight - 28;
		int half = (panelWidth - 32) / 2;
		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.library.copy"), b -> copy())
				.bounds(panelX + 12, y, half, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(panelX + panelWidth - 12 - half, y, half, 20).build());

		search(was);
	}

	/** Every page of every book that holds the word, in the order the books were last written. */
	private void search(String what) {
		found.clear();
		scroll = 0;
		chosen = -1;
		notice = null;
		if (what.isBlank()) {
			return;
		}
		// The same page can stand in several books at once: a chapter copied into a fair copy, a book
		// torn and so filed afresh under a new name, a draft of a draft. They are all the same answer
		// to the same question, and a list that gives it eighty-nine times is no answer at all. The
		// books are in the order they were last written, so the one kept is the newest.
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (BookIO.Kept book : books) {
			for (int page = 0; page < book.document().pageCount(); page++) {
				List<Paragraph> paragraphs = book.document().page(page);
				BookSearch.Hit hit = BookSearch.next(List.of(paragraphs), what, false,
						new BookSearch.Hit(0, 0, 0, 0));
				if (hit == null) {
					continue;
				}
				if (!seen.add(wordsOf(paragraphs))) {
					continue;
				}
				String line = paragraphs.get(hit.paragraph()).text().strip();
				found.add(new Found(book.name(), book.when(), page + 1, line, paragraphs, hit));
			}
		}
		if (found.isEmpty()) {
			notice = Component.translatable("roleplayersquill.find.none").withStyle(ChatFormatting.GRAY);
			return;
		}
		// The first page found is shown at once. The window is here to be looked at, and a preview
		// that waits to be asked for is a preview nobody knows is there.
		chosen = 0;
	}

	/** Everything written on a page, for telling one page from another. */
	private static String wordsOf(List<Paragraph> page) {
		StringBuilder out = new StringBuilder();
		for (Paragraph paragraph : page) {
			out.append(paragraph.text()).append('\n');
		}
		return out.toString();
	}

	private void copy() {
		if (chosen < 0 || chosen >= found.size()) {
			return;
		}
		List<Paragraph> page = found.get(chosen).paragraphs();
		String text = LegacyCodec.encode(page, Layout.lay(page, QuillConfig.get().layoutOptions()));
		Minecraft.getInstance().keyboardHandler.setClipboard(text);
		notice = Component.translatable("roleplayersquill.library.copied").withStyle(ChatFormatting.GRAY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		int button = Input.button(event);
		int row = rowAt(mouseX, mouseY);
		if (row >= 0) {
			chosen = row;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Mth.clamp(scroll - (int) Math.signum(vertical), 0,
				Math.max(0, found.size() - SHOWN));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key(), scanCode = event.keycode(), modifiers = event.modifiers();
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			copy();
			return true;
		}
		// The box keeps the focus while the list is walked through: the word being looked for is
		// still being typed, and the page beside it is what the walking is for.
		if (keyCode == InputConstants.KEY_DOWN) {
			step(1);
			return true;
		}
		if (keyCode == InputConstants.KEY_UP) {
			step(-1);
			return true;
		}
		return super.keyPressed(event);
	}

	/** Moves the choice, and the list under it if the choice has walked off the end. */
	private void step(int by) {
		if (found.isEmpty()) {
			return;
		}
		chosen = Mth.clamp(chosen + by, 0, found.size() - 1);
		if (chosen < scroll) {
			scroll = chosen;
		} else if (chosen >= scroll + SHOWN) {
			scroll = chosen - SHOWN + 1;
		}
		scroll = Mth.clamp(scroll, 0, Math.max(0, found.size() - SHOWN));
	}

	private int rowAt(double mouseX, double mouseY) {
		int top = panelY + 50;
		if (mouseX < panelX + 8 || mouseX > panelX + 8 + LIST) {
			return -1;
		}
		int row = (int) ((mouseY - top) / ROW);
		if (row < 0 || row >= SHOWN) {
			return -1;
		}
		int index = scroll + row;
		return index < found.size() ? index : -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		int top = panelY + 50;
		for (int row = 0; row < SHOWN; row++) {
			int index = scroll + row;
			if (index >= found.size()) {
				break;
			}
			Found entry = found.get(index);
			int y = top + row * ROW;
			if (index == chosen) {
				context.fill(panelX + 8, y - 2, panelX + 8 + LIST, y + ROW - 4, 0x60407090);
			}
			Component heading = Component.literal(entry.book().isEmpty()
					? Component.translatable("roleplayersquill.library.untitled").getString()
					: entry.book())
					.withStyle(ChatFormatting.WHITE)
					.append(Component.literal("  " + Component.translatable("roleplayersquill.library.page",
							entry.page()).getString()).withStyle(ChatFormatting.GOLD));
			context.text(font, trim(heading.getString(), LIST - 12),
					panelX + 12, y, 0xFFFFFFFF, false);
			context.text(font, trim(entry.line(), LIST - 12),
					panelX + 12, y + 10, 0xFF9A9A9A, false);
		}

		drawPreview(context);

		if (notice != null) {
			context.text(font, notice, panelX + 12, panelY + panelHeight - 44,
					0xFFFFFFFF, false);
		} else if (!found.isEmpty()) {
			context.text(font,
					Component.translatable("roleplayersquill.library.count", found.size())
							.withStyle(ChatFormatting.GRAY),
					panelX + 12, panelY + panelHeight - 44, 0xFFFFFFFF, false);
		}
	}

	/**
	 * The chosen page on parchment, with the word that was looked for marked on it.
	 *
	 * <p>Laid out here rather than remembered from the search, because a page is laid out against
	 * the font and the settings of whoever is looking at it, and this is the same code the editor
	 * and the encoder use. The parchment keeps the height of a whole page whether the page is full
	 * or nearly empty, so that walking down the list does not make the frame jump about.
	 */
	private void drawPreview(GuiGraphicsExtractor context) {
		int x = panelX + 20 + LIST;
		int y = panelY + 50;
		int height = Layout.PAGE_LINES * Layout.LINE_HEIGHT;

		context.fill(x - 3, y - 3, x + (int) Layout.PAGE_WIDTH + 3, y + height + 3, 0xFFE9DBBF);
		context.outline(x - 3, y - 3, (int) Layout.PAGE_WIDTH + 6, height + 6, 0xFF6B5A3E);

		if (chosen < 0 || chosen >= found.size()) {
			return;
		}
		Found entry = found.get(chosen);
		List<Paragraph> page = entry.paragraphs();
		List<Layout.LaidLine> lines = Layout.lay(page, QuillConfig.get().layoutOptions());
		for (int i = 0; i < Math.min(lines.size(), Layout.PAGE_LINES); i++) {
			Layout.LaidLine line = lines.get(i);
			int lineY = y + i * Layout.LINE_HEIGHT;
			markOn(context, line, page, entry.hit(), x, lineY);
			BookPreview.drawLine(context, font, line, page, x, lineY);
		}
	}

	/** The mark under the words that were found, where this line carries any of them. */
	private void markOn(GuiGraphicsExtractor context, Layout.LaidLine line, List<Paragraph> page,
			BookSearch.Hit hit, int x, int y) {
		if (line.paragraph != hit.paragraph() || hit.to() <= line.start || hit.from() >= line.contentEnd) {
			return;
		}
		// A match can run over a line break, and then it is marked on both lines, each as far as
		// that line goes.
		int from = Math.max(hit.from(), line.start);
		int to = Math.min(hit.to(), line.contentEnd);
		int left = x + (int) BookPreview.offsetOf(line, page, from);
		int right = x + (int) Math.ceil(BookPreview.offsetOf(line, page, to));
		context.fill(left - 1, y - 1, right + 1, y + Layout.LINE_HEIGHT - 1, MARK);
	}

	private String trim(String text, int width) {
		return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - 6) + "…";
	}

	/** The day a book was last written, for telling two books of the same name apart. */
	static String when(long millis) {
		return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(millis));
	}
}
