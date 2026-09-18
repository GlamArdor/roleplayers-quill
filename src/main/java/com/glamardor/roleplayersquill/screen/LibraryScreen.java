package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookIO;
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
 */
public class LibraryScreen extends DialogScreen {
	private static final int ROW = 26;
	private static final int SHOWN = 6;

	private record Found(String book, long when, int page, String line, List<Paragraph> paragraphs) {
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
		this.panelWidth = 340;
		this.panelHeight = SHOWN * ROW + 104;
		this.books = BookIO.allBooks();
		this.initial = initial;
	}

	private final String initial;

	@Override
	protected void init() {
		super.init();
		String was = needle == null ? initial : needle.getText();
		needle = new TextFieldWidget(textRenderer, panelX + 12, panelY + 24, panelWidth - 24, 18,
				Text.translatable("roleplayersquill.find.needle"));
		needle.setMaxLength(120);
		needle.setPlaceholder(Text.translatable("roleplayersquill.find.needle").formatted(Formatting.DARK_GRAY));
		needle.setText(was);
		needle.setChangedListener(value -> search(value));
		addDrawableChild(needle);
		setInitialFocus(needle);

		int y = panelY + panelHeight - 28;
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.library.copy"), b -> copy())
				.dimensions(panelX + 12, y, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
				.dimensions(panelX + 178, y, 150, 20).build());

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
		for (BookIO.Kept book : books) {
			for (int page = 0; page < book.document().pageCount(); page++) {
				List<Paragraph> paragraphs = book.document().page(page);
				BookSearch.Hit hit = BookSearch.next(List.of(paragraphs), what, false,
						new BookSearch.Hit(0, 0, 0, 0));
				if (hit == null) {
					continue;
				}
				String line = paragraphs.get(hit.paragraph()).text().strip();
				found.add(new Found(book.name(), book.when(), page + 1, line, paragraphs));
			}
		}
		if (found.isEmpty()) {
			notice = Text.translatable("roleplayersquill.find.none").formatted(Formatting.GRAY);
		}
	}

	private void copy() {
		if (chosen < 0 || chosen >= found.size()) {
			return;
		}
		List<Paragraph> page = found.get(chosen).paragraphs();
		String text = LegacyCodec.encode(page, Layout.lay(page, com.glamardor.roleplayersquill.config.QuillConfig
				.get().layoutOptions()));
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
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private int rowAt(double mouseX, double mouseY) {
		int top = panelY + 50;
		if (mouseX < panelX + 8 || mouseX > panelX + panelWidth - 8) {
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
				context.fill(panelX + 8, y - 2, panelX + panelWidth - 8, y + ROW - 4, 0x60407090);
			}
			Text heading = Text.literal(entry.book().isEmpty()
					? Text.translatable("roleplayersquill.library.untitled").getString()
					: entry.book())
					.formatted(Formatting.WHITE)
					.append(Text.literal("  " + Text.translatable("roleplayersquill.library.page",
							entry.page()).getString()).formatted(Formatting.GOLD));
			context.drawText(textRenderer, trim(heading.getString(), panelWidth - 28),
					panelX + 12, y, 0xFFFFFFFF, false);
			context.drawText(textRenderer, trim(entry.line(), panelWidth - 28),
					panelX + 12, y + 10, 0xFF9A9A9A, false);
		}

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

	private String trim(String text, int width) {
		return textRenderer.getWidth(text) <= width ? text : textRenderer.trimToWidth(text, width - 6) + "…";
	}

	/** The day a book was last written, for telling two books of the same name apart. */
	static String when(long millis) {
		return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(millis));
	}
}
