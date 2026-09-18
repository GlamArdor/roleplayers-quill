package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * The book as it stood on earlier days, and the way back to one of them.
 *
 * <p>Undo is for the last few minutes and dies when the history runs out. This is the other
 * question – what did this book say before yesterday's rewriting – and it is answered from disk: a
 * copy is kept every time the book is really written back, and twenty of them are held.
 *
 * <p>Restoring is an ordinary edit. It can be undone, and nothing is sent anywhere until the book is
 * written back in the usual way, so a version opened by mistake costs a Ctrl+Z.
 */
public class HistoryScreen extends DialogScreen {
	private static final int ROW = 24;
	private static final int SHOWN = 7;
	/** How wide the list of dates is; the page being looked at stands to the right of it. */
	private static final int LIST = 176;

	private final PageEditor editor;
	private final List<BookIO.Version> versions;
	private int scroll;
	private int chosen;
	@Nullable
	private Text notice;

	/** The version being looked at, read from disk once and kept while it stays chosen. */
	@Nullable
	private List<List<Paragraph>> preview;
	private int previewOf = -1;
	/** Which page of that version is on show. A version is a whole book, not its first page. */
	private int previewPage;
	@Nullable
	private ButtonWidget back;
	@Nullable
	private ButtonWidget forth;

	public HistoryScreen(@Nullable Screen parent, PageEditor editor) {
		super(parent, Text.translatable("roleplayersquill.history.title"));
		this.editor = editor;
		this.versions = BookIO.versions(editor.document().id());
		this.panelWidth = 336;
		this.panelHeight = SHOWN * ROW + 82;
	}

	/**
	 * The chosen version's first page, read when the choice changes and not on every frame.
	 *
	 * <p>A list of dates says nothing about which evening's work is which. The page is what tells
	 * them apart, and restoring the wrong one silently is exactly what this window exists to stop.
	 */
	@Nullable
	private List<List<Paragraph>> preview() {
		if (chosen < 0 || chosen >= versions.size()) {
			return null;
		}
		if (previewOf != chosen) {
			preview = BookIO.readVersion(versions.get(chosen).file());
			previewOf = chosen;
			previewPage = 0;
		}
		return preview;
	}

	/** How many pages the version on show has, zero when there is nothing to show. */
	private int previewPages() {
		List<List<Paragraph>> pages = preview();
		return pages == null ? 0 : pages.size();
	}

	private void turnPreview(int by) {
		int pages = previewPages();
		if (pages > 0) {
			previewPage = MathHelper.clamp(previewPage + by, 0, pages - 1);
		}
	}

	@Override
	protected void init() {
		super.init();
		int y = panelY + panelHeight - 28;
		// Halves of the room inside the panel rather than two fixed widths: at 150 apiece the first
		// button ran four pixels into the second one.
		int half = (panelWidth - 32) / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.history.restore"),
						b -> restore())
				.dimensions(panelX + 12, y, half, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
				.dimensions(panelX + panelWidth - 12 - half, y, half, 20).build());

		int turnY = pageTop() + Layout.PAGE_LINES * Layout.LINE_HEIGHT + 7;
		back = addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> turnPreview(-1))
				.dimensions(pageLeft(), turnY, 18, 16).build());
		forth = addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> turnPreview(1))
				.dimensions(pageLeft() + (int) Layout.PAGE_WIDTH - 18, turnY, 18, 16).build());
	}

	private int pageLeft() {
		return panelX + 16 + LIST;
	}

	private int pageTop() {
		return panelY + 26;
	}

	private void restore() {
		if (chosen < 0 || chosen >= versions.size()) {
			return;
		}
		List<List<Paragraph>> pages = preview();
		if (pages == null || pages.isEmpty()) {
			notice = Text.translatable("roleplayersquill.history.unreadable").formatted(Formatting.RED);
			return;
		}
		editor.document().mark();
		editor.document().pages().clear();
		for (List<Paragraph> page : pages) {
			editor.document().pages().add(QuillDocument.copyPage(page));
		}
		// Onto the page that was being looked at: that is the one the choice was made on.
		editor.setPage(Math.min(previewPage, pages.size() - 1));
		editor.touch();
		close();
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

	/** The wheel turns whatever is under it: the list of dates on the left, the pages on the right. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int step = (int) Math.signum(vertical);
		if (mouseX > pageLeft() - 4) {
			// Down the wheel is on through the book, the way every reader turns pages, and the opposite
			// of the way the same wheel moves a list.
			turnPreview(step);
			return true;
		}
		scroll = MathHelper.clamp(scroll - step, 0, Math.max(0, versions.size() - SHOWN));
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
			turnPreview(-1);
			return true;
		}
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
			turnPreview(1);
			return true;
		}
		return super.keyPressed(key, scancode, modifiers);
	}

	private int rowAt(double mouseX, double mouseY) {
		int top = panelY + 26;
		if (mouseX < panelX + 8 || mouseX > panelX + 8 + LIST) {
			return -1;
		}
		int row = (int) ((mouseY - top) / ROW);
		if (row < 0 || row >= SHOWN) {
			return -1;
		}
		int index = scroll + row;
		return index < versions.size() ? index : -1;
	}

	/**
	 * Whether the arrows are there at all, decided before the widgets are drawn rather than after.
	 *
	 * <p>A one-page version has nowhere to turn to, and a book with no history has no pages, so the
	 * arrows go away instead of standing there doing nothing.
	 */
	private void updateArrows() {
		int pages = versions.isEmpty() ? 0 : previewPages();
		if (back != null) {
			back.visible = pages > 1;
			back.active = previewPage > 0;
		}
		if (forth != null) {
			forth.visible = pages > 1;
			forth.active = previewPage < pages - 1;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		updateArrows();
		super.render(context, mouseX, mouseY, delta);

		if (versions.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("roleplayersquill.history.none").formatted(Formatting.GRAY),
					width / 2, panelY + 40, 0xFFFFFFFF);
			return;
		}

		int top = panelY + 26;
		SimpleDateFormat stamp = new SimpleDateFormat("d MMMM, HH:mm");
		for (int row = 0; row < SHOWN; row++) {
			int index = scroll + row;
			if (index >= versions.size()) {
				break;
			}
			BookIO.Version version = versions.get(index);
			int y = top + row * ROW;
			if (index == chosen) {
				context.fill(panelX + 8, y - 2, panelX + 8 + LIST, y + ROW - 4, 0x60407090);
			}
			context.drawText(textRenderer, Text.literal(stamp.format(new Date(version.when())))
					.formatted(index == 0 ? Formatting.WHITE : Formatting.GRAY), panelX + 12, y, 0xFFFFFFFF, false);
			String about = Text.translatable("roleplayersquill.history.pages", version.pages()).getString();
			if (!version.title().isBlank()) {
				about = version.title() + " · " + about;
			}
			context.drawText(textRenderer, Text.literal(about).formatted(Formatting.DARK_GRAY),
					panelX + 12, y + 10, 0xFFFFFFFF, false);
		}

		drawPreview(context);

		if (notice != null) {
			context.drawText(textRenderer, notice, panelX + 12, panelY + panelHeight - 42, 0xFFFFFFFF, false);
		}
	}

	/**
	 * The chosen version, page by page, laid out the way the book lays it out.
	 *
	 * <p>The parchment keeps the height of a whole page whichever page is on it, so that turning
	 * through a version does not make the frame jump about as the pages fill or empty.
	 */
	private void drawPreview(DrawContext context) {
		List<List<Paragraph>> pages = preview();
		int x = pageLeft();
		int y = pageTop();
		int height = Layout.PAGE_LINES * Layout.LINE_HEIGHT;

		context.fill(x - 3, y - 3, x + (int) Layout.PAGE_WIDTH + 3, y + height + 3, 0xFFE9DBBF);
		context.drawBorder(x - 3, y - 3, (int) Layout.PAGE_WIDTH + 6, height + 6, 0xFF6B5A3E);

		if (pages == null || pages.isEmpty()) {
			return;
		}

		previewPage = MathHelper.clamp(previewPage, 0, pages.size() - 1);
		List<Paragraph> page = pages.get(previewPage);
		List<Layout.LaidLine> lines = Layout.lay(page, editor.layoutOptions());
		for (int i = 0; i < Math.min(lines.size(), Layout.PAGE_LINES); i++) {
			BookPreview.drawLine(context, textRenderer, lines.get(i), page, x, y + i * Layout.LINE_HEIGHT);
		}

		Text count = Text.literal((previewPage + 1) + "/" + pages.size()).formatted(Formatting.GRAY);
		context.drawText(textRenderer, count,
				x + ((int) Layout.PAGE_WIDTH - textRenderer.getWidth(count)) / 2, y + height + 11,
				0xFFFFFFFF, false);
	}
}
