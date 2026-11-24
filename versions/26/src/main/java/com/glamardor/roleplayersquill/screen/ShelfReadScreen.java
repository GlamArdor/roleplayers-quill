package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.input.KeyEvent;

import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A book off the shelf, read again page by page.
 *
 * <p>It is not an item and there is no book screen to open it in – what is here is a document on
 * disk – so the page is drawn the way every other window in this mod draws a page it is showing
 * rather than editing: laid out by {@link Layout}, put down by {@link BookPreview}, on parchment.
 *
 * <p>Everything that works on a book being written works here through {@link BookView}: the
 * contents, the list of pages and the export are the same three screens, told that this book cannot
 * be written to. Which is the truth about it – it is a copy of something that may not exist any
 * more, and the way to change it is to write it back into a real book first.
 */
public class ShelfReadScreen extends DialogScreen {
	private final BookIO.Kept book;
	@Nullable
	private final PageEditor editor;
	private final View view = new View();

	private int page;
	@Nullable
	private Component notice;
	@Nullable
	private Button back;
	@Nullable
	private Button forth;

	public ShelfReadScreen(@Nullable Screen parent, BookIO.Kept book, @Nullable PageEditor editor) {
		super(parent, Component.literal(book.name().isBlank()
				? Component.translatable("roleplayersquill.library.untitled").getString()
				: book.name()));
		this.book = book;
		this.editor = editor;
		this.panelWidth = 300;
		this.panelHeight = 36 + Layout.PAGE_LINES * Layout.LINE_HEIGHT + 82;
	}

	@Override
	protected void init() {
		super.init();

		// Beside the page rather than under it, level with its middle: the room under the page is
		// where the page number goes, and a book is turned from its edges.
		int turnY = pageTop() + (Layout.PAGE_LINES * Layout.LINE_HEIGHT - 20) / 2;
		back = addRenderableWidget(Button.builder(Component.literal("◀"), b -> turn(-1))
				.bounds(pageLeft() - 32, turnY, 20, 20).build());
		forth = addRenderableWidget(Button.builder(Component.literal("▶"), b -> turn(1))
				.bounds(pageLeft() + (int) Layout.PAGE_WIDTH + 12, turnY, 20, 20).build());

		int gap = 4;
		int row = panelY + panelHeight - 52;
		int each = (panelWidth - 24 - gap * 2) / 3;
		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.tool.contents"),
						b -> Minecraft.getInstance().gui.setScreen(new ContentsScreen(this, view)))
				.bounds(panelX + 12, row, each, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.tool.pages"),
						b -> Minecraft.getInstance().gui.setScreen(new PagesScreen(this, view)))
				.bounds(panelX + 12 + each + gap, row, each, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.tool.export"),
						b -> Minecraft.getInstance().gui.setScreen(new ExportScreen(this, view)))
				.bounds(panelX + 12 + (each + gap) * 2, row, each, 20).build());

		int bottom = panelY + panelHeight - 28;
		if (editor != null) {
			int half = (panelWidth - 24 - gap) / 2;
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.shelf.restore"),
							b -> restore())
					.bounds(panelX + 12, bottom, half, 20).build());
			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
					.bounds(panelX + 12 + half + gap, bottom, half, 20).build());
		} else {
			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
					.bounds(panelX + 12, bottom, panelWidth - 24, 20).build());
		}
	}

	private int pageLeft() {
		return panelX + (panelWidth - (int) Layout.PAGE_WIDTH) / 2;
	}

	private int pageTop() {
		return panelY + 36;
	}

	private void turn(int by) {
		page = Mth.clamp(page + by, 0, book.document().pageCount() - 1);
	}

	private void restore() {
		if (editor == null) {
			return;
		}
		int dropped = ShelfScreen.restoreInto(editor, book.document());
		if (dropped > 0) {
			notice = Component.translatable("roleplayersquill.shelf.restored.partly", dropped)
					.withStyle(ChatFormatting.GOLD);
			return;
		}
		// All the way out, past the shelf: the book is in hand now, and what was wanted next was the
		// page it was restored onto rather than the list it came from.
		Screen target = this;
		while (target instanceof DialogScreen dialog && dialog.parent != null) {
			target = dialog.parent;
		}
		Minecraft.getInstance().gui.setScreen(target);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key(), scancode = event.keycode(), modifiers = event.modifiers();
		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_PAGEUP) {
			turn(-1);
			return true;
		}
		if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_PAGEDOWN) {
			turn(1);
			return true;
		}
		return super.keyPressed(event);
	}

	/** Down the wheel turns the page on, the way every reader turns pages. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		turn((int) Math.signum(vertical));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		int pages = book.document().pageCount();
		if (back != null) {
			back.active = page > 0;
		}
		if (forth != null) {
			forth.active = page < pages - 1;
		}
		super.extractRenderState(context, mouseX, mouseY, delta);

		if (!book.author().isBlank()) {
			context.centeredText(font,
					Component.literal(book.author()).withStyle(ChatFormatting.GRAY), width / 2, panelY + 22,
					0xFFFFFFFF);
		}

		int x = pageLeft();
		int y = pageTop();
		int height = Layout.PAGE_LINES * Layout.LINE_HEIGHT;
		context.fill(x - 4, y - 4, x + (int) Layout.PAGE_WIDTH + 4, y + height + 4, 0xFFE9DBBF);
		context.outline(x - 4, y - 4, (int) Layout.PAGE_WIDTH + 8, height + 8, 0xFF6B5A3E);

		page = Mth.clamp(page, 0, pages - 1);
		List<Paragraph> shown = book.document().page(page);
		List<Layout.LaidLine> lines = Layout.lay(shown, QuillConfig.get().layoutOptions());
		for (int i = 0; i < Math.min(lines.size(), Layout.PAGE_LINES); i++) {
			BookPreview.drawLine(context, font, lines.get(i), shown, x, y + i * Layout.LINE_HEIGHT);
		}

		Component count = notice != null ? notice
				: Component.literal((page + 1) + "/" + pages).withStyle(ChatFormatting.GRAY);
		context.centeredText(font, count, width / 2, y + height + 10, 0xFFFFFFFF);
	}

	/**
	 * The kept book behind the three screens that work on any book at all.
	 *
	 * <p>Nothing can be changed through it. Turning a page moves this window, because the page list
	 * jumping to a page is the one thing here that is an instruction rather than a question.
	 */
	private final class View implements BookView {
		@Override
		public QuillDocument document() {
			return book.document();
		}

		@Override
		public int page() {
			return page;
		}

		@Override
		public void setPage(int index) {
			page = Mth.clamp(index, 0, book.document().pageCount() - 1);
		}

		@Override
		public List<String> encodePages() {
			List<String> out = new ArrayList<>(book.document().pageCount());
			for (List<Paragraph> written : book.document().pages()) {
				out.add(LegacyCodec.encode(written, Layout.lay(written, QuillConfig.get().layoutOptions())));
			}
			return out;
		}
	}
}
