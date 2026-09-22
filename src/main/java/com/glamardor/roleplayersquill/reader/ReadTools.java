package com.glamardor.roleplayersquill.reader;

import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.ContentsScreen;
import com.glamardor.roleplayersquill.screen.ExportScreen;
import com.glamardor.roleplayersquill.screen.FindBar;
import com.glamardor.roleplayersquill.screen.IconButton;
import com.glamardor.roleplayersquill.screen.Icons;
import com.glamardor.roleplayersquill.screen.PagesScreen;
import com.glamardor.roleplayersquill.screen.ShelfScreen;
import com.glamardor.roleplayersquill.text.Layout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Everything this mod adds around a book being read: the buttons beside it, the strip under it
 * for finding, the header over it, and the selection on it.
 *
 * <p>One of these belongs to each {@code BookScreen} – made the first time {@code init} runs on
 * it and kept by {@link com.glamardor.roleplayersquill.mixin.BookScreenMixin} for as long as the
 * screen is open, so that a resize or a find-bar toggle, both of which rebuild the widget list,
 * neither loses the selection nor forgets a match halfway through being walked.
 */
public final class ReadTools {
	/** The vanilla book: a fixed 192 by 192, drawn at a y of 2 whatever the screen is. */
	private static final int BOOK_TOP = 2;
	private static final int BOOK_SIZE = 192;
	/**
	 * The first line below the book that is ours to draw on.
	 *
	 * <p>Vanilla's own Done button stands at a y of 196 and is twenty tall, dead centre and two
	 * hundred wide – so everything this mod puts under the book starts below that, or it lands on
	 * top of the one button the reader actually needs.
	 */
	private static final int BELOW_DONE = 220;
	private static final int LINE = 13;

	private final ReadHost host;
	private final ReadView view;
	private final ReadSelection selection = new ReadSelection();

	@Nullable
	private FindBar findBar;

	@Nullable
	private PageText cachedPageText;
	private int cachedPageIndex = -1;

	/** What was just done, said on the line under the book until it has been there long enough. */
	@Nullable
	private Text notice;
	private long noticeUntil;

	private boolean mouseWasDown;
	/**
	 * True for the rest of a button held down after it picked out a word or a line. The button is
	 * still down the very next frame, sitting somewhere inside what was just selected; an ordinary
	 * drag reading the pointer there would narrow the selection straight back down to that point.
	 */
	private boolean suppressDrag;

	public ReadTools(ReadHost host) {
		this.host = host;
		this.view = new ReadView(host);
	}

	public ReadSelection selection() {
		return selection;
	}

	// ---- widgets, added at the tail of the screen's own init ------------------------------------

	public void addWidgets(int screenWidth, int screenHeight, Consumer<ClickableWidget> add,
			TextRenderer textRenderer) {
		QuillConfig config = QuillConfig.get();
		int left = (screenWidth - BOOK_SIZE) / 2;
		int x = left + BOOK_SIZE + 6;
		int size = IconButton.SIZE + 2;

		if (config.readerTools && x + IconButton.SIZE <= screenWidth) {
			int y = 4;
			// "Find", not "Find and replace": the button opens the same strip the editor's does, but
			// a signed book cannot be written in, so half of what the editor's button promises is
			// not on offer here.
			add.accept(new IconButton(x, y, Icons.FIND, Text.translatable("roleplayersquill.tool.find.read"),
					this::toggleFind).showing(() -> findBar != null));
			y += size;
			add.accept(new IconButton(x, y, Icons.CONTENTS, Text.translatable("roleplayersquill.tool.contents"),
					() -> open(new ContentsScreen(host.asScreen(), view))));
			y += size;
			add.accept(new IconButton(x, y, Icons.PAGES, Text.translatable("roleplayersquill.tool.pages"),
					() -> open(new PagesScreen(host.asScreen(), view))));
			y += size;
			add.accept(new IconButton(x, y, Icons.SAVE, Text.translatable("roleplayersquill.reader.keep"),
					this::keep));
			y += size;
			add.accept(new IconButton(x, y, Icons.SHELF, Text.translatable("roleplayersquill.tool.shelf"),
					() -> open(new ShelfScreen(host.asScreen(), null))));
			y += size;
			add.accept(new IconButton(x, y, Icons.EXPORT, Text.translatable("roleplayersquill.tool.export"),
					() -> open(new ExportScreen(host.asScreen(), view))));
		}

		if (config.readerTools && findBar != null) {
			findBar.layout(screenWidth / 2, findBarY(screenHeight), screenWidth, textRenderer, add);
			if (findBar.box() != null) {
				host.asScreen().setFocused(findBar.box());
				findBar.box().setFocused(true);
			}
		}
	}

	/**
	 * Under the book, under the Done button, and under the line kept for the header – but never
	 * off the bottom of a screen too short to hold all three.
	 */
	private static int findBarY(int screenHeight) {
		int wanted = BELOW_DONE + LINE;
		return Math.max(BOOK_TOP + BOOK_SIZE + 4, Math.min(wanted, screenHeight - FindBar.height() - 6));
	}

	private void open(Screen dialog) {
		MinecraftClient.getInstance().setScreen(dialog);
	}

	/**
	 * Keeps a copy, and says so.
	 *
	 * <p>Writing a file is the one thing a button can do that leaves no mark on the screen at all,
	 * so without a word about it the button looks broken whether it worked or not.
	 */
	private void keep() {
		boolean kept = BookIO.keepSigned(view.document(), view.encodePages());
		say(kept
				? Text.translatable("roleplayersquill.reader.kept").formatted(Formatting.GREEN)
				: Text.translatable("roleplayersquill.reader.kept.failed").formatted(Formatting.RED));
	}

	/**
	 * Puts the book on the shelf the moment it is opened, and says nothing about it.
	 *
	 * <p>Because the moment a copy is wanted is the moment there is no longer a book to press a
	 * button on: the book was in the hand of somebody who is now dead, or it was lent back, or the
	 * lectern it stood on has been broken. A book that has been read is a book that was in front of
	 * this client once, and keeping it then costs a file nobody notices.
	 *
	 * <p>Filed under what is written in it, so reading the same book every day keeps one copy of it
	 * rather than one a day. A blank book is not kept at all: {@link BookIO#keepSigned} refuses it.
	 */
	public void keepOnOpen() {
		if (!QuillConfig.get().keepOpenedBooks) {
			return;
		}
		try {
			BookIO.keepSigned(view.document(), view.encodePages());
		} catch (RuntimeException error) {
			// A book that will not be copied is still a book that can be read.
			com.glamardor.roleplayersquill.RoleplayersQuill.LOGGER.warn("Could not shelve this book", error);
		}
	}

	/** A line under the book for a few seconds, the way the editor answers for what it just did. */
	private void say(Text message) {
		notice = message;
		noticeUntil = System.currentTimeMillis() + 4000L;
	}

	private void toggleFind() {
		if (findBar == null) {
			// The window with replacing in it never opens from here – there is nothing to replace in
			// a book nobody can write to, and FindBar leaves its button off for exactly that reason.
			findBar = new FindBar(view, () -> { }, this::closeFind);
		} else {
			closeFind();
			return;
		}
		host.reinit();
	}

	private void closeFind() {
		findBar = null;
		view.clearMatch();
		host.reinit();
	}

	// ---- input ------------------------------------------------------------------------------------

	public boolean keyPressed(int keyCode, TextRenderer textRenderer) {
		if (!QuillConfig.get().readerTools) {
			return false;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && findBar != null) {
			closeFind();
			return true;
		}
		boolean boxFocused = findBar != null && findBar.box() != null && findBar.box().isFocused();
		if (boxFocused && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			findBar.step(!Screen.hasShiftDown());
			return true;
		}
		if (boxFocused) {
			// Everything else in the box – typing, its own copy and select-all – is its business.
			return false;
		}
		if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
			toggleFind();
			return true;
		}
		if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
			selection.copy(MinecraftClient.getInstance(), currentPage(textRenderer));
			return true;
		}
		if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
			selection.selectAll(currentPage(textRenderer));
			return true;
		}
		return false;
	}

	private long lastClickTime;
	private int clickCount;

	/** A double click picks out a word, a triple click a line – the same as the editor. */
	public void mousePressed(int button, TextRenderer textRenderer, int screenWidth, double mouseX, double mouseY) {
		if (!QuillConfig.get().readerTools || button != 0) {
			return;
		}
		int[] origin = textOrigin(screenWidth);
		PageText page = currentPage(textRenderer);

		long now = System.currentTimeMillis();
		clickCount = now - lastClickTime < 250L ? clickCount + 1 : 1;
		lastClickTime = now;

		if (clickCount == 2) {
			suppressDrag = selection.selectWord(page, textRenderer, origin[0], origin[1], mouseX, mouseY);
		} else if (clickCount >= 3) {
			suppressDrag = selection.selectLine(page, textRenderer, origin[0], origin[1], mouseX, mouseY);
		} else {
			selection.press(page, textRenderer, origin[0], origin[1], mouseX, mouseY);
			suppressDrag = false;
		}
	}

	// ---- rendering --------------------------------------------------------------------------------

	/**
	 * The dark strip the find bar stands on, drawn before the widgets rather than after them.
	 *
	 * <p>Everything else this class draws goes at the tail of the book's own render, which is after
	 * the screen has drawn its widgets – and that is where it belongs, since a selection is drawn
	 * over the page. The strip is the one thing that is a background: drawn at the tail it went on
	 * top of the very box it is the backing for, and left the text field looking greyed out and the
	 * words in it half washed away.
	 */
	public void renderUnder(DrawContext context) {
		if (QuillConfig.get().readerTools && findBar != null) {
			findBar.renderBehind(context);
		}
	}

	public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight,
			double mouseX, double mouseY) {
		QuillConfig config = QuillConfig.get();
		int[] origin = textOrigin(screenWidth);

		renderBanner(context, textRenderer, screenWidth, screenHeight);

		if (!config.readerTools) {
			return;
		}

		PageText page = currentPage(textRenderer);
		// A dialog standing over the book draws the book behind it, which brings us back through
		// here every frame. The pointer belongs to the panel then, not to the page under it.
		boolean active = MinecraftClient.getInstance().currentScreen == host.asScreen();
		boolean down = active && leftButtonHeld();
		if (down && mouseWasDown && !suppressDrag) {
			selection.drag(page, textRenderer, origin[0], origin[1], mouseX, mouseY);
		} else if (!down && mouseWasDown) {
			selection.release();
			suppressDrag = false;
		}
		mouseWasDown = down;

		selection.renderSelection(context, page, textRenderer, origin[0], origin[1]);
		if (findBar != null && view.matchPage() == host.pageIndex()) {
			selection.renderMatch(context, page, textRenderer, origin[0], origin[1],
					view.matchNeedle(), view.matchOrdinalOnPage());
		}
		if (findBar != null) {
			findBar.renderTally(context, textRenderer);
		}
	}

	/**
	 * The one line under the book: who wrote this and which copy it is, or – while there is one to
	 * give – the answer to whatever was just pressed.
	 *
	 * <p>Under the book rather than over it. The vanilla page is drawn from a y of two and fills
	 * everything down to the Done button, and the strip along its top already carries the page
	 * count; a line put up there landed on the border and read as something gone wrong. Below the
	 * Done button there is nothing, and one line is all this needs.
	 */
	private void renderBanner(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
		if (screenHeight < BELOW_DONE + LINE) {
			return;
		}
		Text line = currentNotice();
		if (line == null) {
			if (!QuillConfig.get().readerHeader) {
				return;
			}
			line = header(textRenderer, screenWidth - 20);
			if (line == null) {
				return;
			}
		}
		context.drawCenteredTextWithShadow(textRenderer, line, screenWidth / 2, BELOW_DONE, 0xFFFFFFFF);
	}

	@Nullable
	private Text currentNotice() {
		return notice != null && System.currentTimeMillis() <= noticeUntil ? notice : null;
	}

	@Nullable
	private Text header(TextRenderer textRenderer, int room) {
		SignedBook.Info info = SignedBook.infoFor(host.asScreen());
		if (info == null) {
			return null;
		}
		String title = info.title();
		if (title.isBlank()) {
			title = Text.translatable("roleplayersquill.library.untitled").getString();
		}
		String key = info.isTattered() ? "roleplayersquill.reader.header.tattered"
				: info.isCopy() ? "roleplayersquill.reader.header.copy"
				: "roleplayersquill.reader.header.original";
		String line = Text.translatable(key, title, info.author()).getString();
		// A book can be called anything and signed by anybody; the line is cut rather than allowed
		// to run off both sides of the screen.
		return Text.literal(textRenderer.trimToWidth(line, Math.max(40, room))).formatted(Formatting.GRAY);
	}

	/**
	 * Whether the left button is held down this very moment.
	 *
	 * <p>Asked of the window rather than of the game's own {@code Mouse}, whose flag is about a
	 * click having happened rather than a button being down – which is a different question, and
	 * answering the wrong one left every drag on the page doing nothing at all.
	 */
	private static boolean leftButtonHeld() {
		long window = MinecraftClient.getInstance().getWindow().getHandle();
		return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
	}

	private PageText currentPage(TextRenderer textRenderer) {
		int index = host.pageIndex();
		if (cachedPageText == null || cachedPageIndex != index) {
			// Also the one place a page turn is noticed regardless of what caused it – the side
			// buttons, the vanilla arrows, Page Up, a lectern synced from the server. A selection
			// belongs to the lines it was dragged across, and those lines just changed under it.
			if (cachedPageText != null) {
				selection.clear();
			}
			cachedPageIndex = index;
			cachedPageText = PageText.of(textRenderer.wrapLines(host.contents().getPage(index), (int) Layout.PAGE_WIDTH));
		}
		return cachedPageText;
	}

	/** Where the vanilla page's text begins: the same numbers {@code BookScreen.render} draws with. */
	private static int[] textOrigin(int screenWidth) {
		int left = (screenWidth - BOOK_SIZE) / 2;
		return new int[] { left + 36, 32 };
	}
}
