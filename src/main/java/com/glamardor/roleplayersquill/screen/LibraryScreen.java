package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.text.BookSearch;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.Paragraph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
	private TextFieldWidget needle;
	private int scroll;
	private int chosen = -1;
	@Nullable
	private Text notice;

	public LibraryScreen(@Nullable Screen parent, String initial) {
		super(parent, Text.translatable("roleplayersquill.library.title"));
		this.panelWidth = LIST + (int) Layout.PAGE_WIDTH + 48;
		this.panelHeight = SHOWN * ROW + 104;
		this.books = BookIO.allBooks();
		this.initial = initial;
	}

	private final String initial;

	@Override
	protected void init() {
		super.init();
		String was = needle == null ? initial : needle.getText();
		needle = new TextFieldWidget(textRenderer, panelX + 12, panelY + 24, LIST - 4, 18,
				Text.translatable("roleplayersquill.find.needle"));
		needle.setMaxLength(120);
		needle.setPlaceholder(Text.translatable("roleplayersquill.find.needle").formatted(Formatting.DARK_GRAY));
		needle.setText(was);
		needle.setChangedListener(value -> search(value));
		addDrawableChild(needle);
		setInitialFocus(needle);

		int y = panelY + panelHeight - 28;
		int half = (panelWidth - 32) / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.library.copy"), b -> copy())
				.dimensions(panelX + 12, y, half, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
				.dimensions(panelX + panelWidth - 12 - half, y, half, 20).build());

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
			notice = Text.translatable("roleplayersquill.find.none").formatted(Formatting.GRAY);
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
		MinecraftClient.getInstance().keyboard.setClipboard(text);
		notice = Text.translatable("roleplayersquill.library.copied").formatted(Formatting.GRAY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int row = rowAt(mouseX, mouseY);
		if (row >= 0) {
			chosen = row;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = MathHelper.clamp(scroll - (int) Math.signum(vertical), 0,
				Math.max(0, found.size() - SHOWN));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			copy();
			return true;
		}
		// The box keeps the focus while the list is walked through: the word being looked for is
		// still being typed, and the page beside it is what the walking is for.
		if (keyCode == GLFW.GLFW_KEY_DOWN) {
			step(1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_UP) {
			step(-1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Moves the choice, and the list under it if the choice has walked off the end. */
	private void step(int by) {
		if (found.isEmpty()) {
			return;
		}
		chosen = MathHelper.clamp(chosen + by, 0, found.size() - 1);
		if (chosen < scroll) {
			scroll = chosen;
		} else if (chosen >= scroll + SHOWN) {
			scroll = chosen - SHOWN + 1;
		}
		scroll = MathHelper.clamp(scroll, 0, Math.max(0, found.size() - SHOWN));
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
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

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
			Text heading = Text.literal(entry.book().isEmpty()
					? Text.translatable("roleplayersquill.library.untitled").getString()
					: entry.book())
					.formatted(Formatting.WHITE)
					.append(Text.literal("  " + Text.translatable("roleplayersquill.library.page",
							entry.page()).getString()).formatted(Formatting.GOLD));
			context.drawText(textRenderer, trim(heading.getString(), LIST - 12),
					panelX + 12, y, 0xFFFFFFFF, false);
			context.drawText(textRenderer, trim(entry.line(), LIST - 12),
					panelX + 12, y + 10, 0xFF9A9A9A, false);
		}

		drawPreview(context);

		if (notice != null) {
			context.drawText(textRenderer, notice, panelX + 12, panelY + panelHeight - 44,
					0xFFFFFFFF, false);
		} else if (!found.isEmpty()) {
			context.drawText(textRenderer,
					Text.translatable("roleplayersquill.library.count", found.size())
							.formatted(Formatting.GRAY),
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
	private void drawPreview(DrawContext context) {
		int x = panelX + 20 + LIST;
		int y = panelY + 50;
		int height = Layout.PAGE_LINES * Layout.LINE_HEIGHT;

		context.fill(x - 3, y - 3, x + (int) Layout.PAGE_WIDTH + 3, y + height + 3, 0xFFE9DBBF);
		context.drawBorder(x - 3, y - 3, (int) Layout.PAGE_WIDTH + 6, height + 6, 0xFF6B5A3E);

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
			BookPreview.drawLine(context, textRenderer, line, page, x, lineY);
		}
	}

	/** The mark under the words that were found, where this line carries any of them. */
	private void markOn(DrawContext context, Layout.LaidLine line, List<Paragraph> page,
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
		return textRenderer.getWidth(text) <= width ? text : textRenderer.trimToWidth(text, width - 6) + "…";
	}

	/** The day a book was last written, for telling two books of the same name apart. */
	static String when(long millis) {
		return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(millis));
	}
}
