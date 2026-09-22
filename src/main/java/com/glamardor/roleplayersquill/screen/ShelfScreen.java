package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
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
import java.util.Locale;

/**
 * The shelf: every book this computer has ever had open, whole, to be read again or written back
 * into a blank one.
 *
 * <p>{@link LibraryScreen} answers "which book was it that mentioned the harbour" and hands over a
 * page. This answers the other two questions, which are the ones asked after something has gone
 * wrong: let me read that book again, and – the reason this exists at all – give me that book back.
 * A book dropped on death and burnt, a book lent and not returned, a book a plugin ate: the text of
 * it was in front of this client once, so it is here, and a blank book plus one press is the whole
 * of the recovery. Not page by page through the clipboard, which is what it used to take.
 *
 * <p>What is on the shelf is what was read, kept when the book was opened, and what was written,
 * kept as the draft it was being written into. Both are the same thing by the time they are here:
 * a title, an author where there was one, and the pages.
 */
public class ShelfScreen extends DialogScreen {
	private static final int ROW = 26;
	private static final int SHOWN = 6;
	/** How wide the list is; the page of the chosen book stands to the right of it. */
	private static final int LIST = 208;
	/** The room a star takes at the right end of a row, and the width of the bar beside the list. */
	private static final int STAR = 16;
	private static final int BAR = 5;

	/** The editor to write a book back into, when the shelf was opened from one. */
	@Nullable
	private final PageEditor editor;

	private final List<BookIO.Kept> books;
	private final List<BookIO.Kept> shown = new ArrayList<>();
	private TextFieldWidget needle;
	private int scroll;
	private int chosen = -1;
	@Nullable
	private Text notice;
	/**
	 * Whether the next press of Delete is the one that does it.
	 *
	 * <p>A book on this shelf can be the only copy left of something, and a list where one row is
	 * one press away from gone is a list nobody should have to walk carefully.
	 */
	private boolean deleteArmed;
	/** Whether the bar beside the list is being dragged, which the wheel and the rows are not. */
	private boolean draggingBar;
	@Nullable
	private ButtonWidget deleteButton;
	@Nullable
	private ButtonWidget restoreButton;
	@Nullable
	private ButtonWidget readButton;

	public ShelfScreen(@Nullable Screen parent, @Nullable PageEditor editor) {
		super(parent, Text.translatable("roleplayersquill.shelf.title"));
		this.editor = editor;
		this.books = BookIO.allBooks();
		this.panelWidth = LIST + (int) Layout.PAGE_WIDTH + 48;
		this.panelHeight = SHOWN * ROW + 104;
	}

	@Override
	protected void init() {
		super.init();
		// A window rebuilt – resized, or come back to – is a window nobody is mid-press in, and an
		// armed Delete that survived it would be a button that deletes on the first press.
		deleteArmed = false;
		String was = needle == null ? "" : needle.getText();
		needle = new TextFieldWidget(textRenderer, panelX + 12, panelY + 24, LIST - 4, 18,
				Text.translatable("roleplayersquill.shelf.search"));
		needle.setMaxLength(60);
		needle.setPlaceholder(Text.translatable("roleplayersquill.shelf.search").formatted(Formatting.DARK_GRAY));
		needle.setText(was);
		needle.setChangedListener(this::filter);
		addDrawableChild(needle);
		setInitialFocus(needle);

		int y = panelY + panelHeight - 28;
		int gap = 4;
		// Four buttons where the shelf can write a book back, three where it cannot: nothing here
		// stands greyed out, because a grey button says only that something is wrong and never that
		// the answer is "open this from a book you are writing".
		int count = editor != null ? 4 : 3;
		int room = panelWidth - 24 - gap * (count - 1);
		int each = room / count;
		int x = panelX + 12;

		readButton = addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.shelf.read"),
						b -> read())
				.dimensions(x, y, each, 20).build());
		x += each + gap;
		if (editor != null) {
			restoreButton = addDrawableChild(ButtonWidget.builder(
							Text.translatable("roleplayersquill.shelf.restore"), b -> restore())
					.dimensions(x, y, each, 20).build());
			x += each + gap;
		}
		deleteButton = addDrawableChild(ButtonWidget.builder(deleteLabel(), b -> delete())
				.dimensions(x, y, each, 20).build());
		x += each + gap;
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
				.dimensions(x, y, panelX + panelWidth - 12 - x, 20).build());

		filter(was);
	}

	// ---- which books are on show -----------------------------------------------------------------

	/** The shelf narrowed to whatever was typed: a title, an author, or a word off the first page. */
	private void filter(String what) {
		BookIO.Kept had = chosen >= 0 && chosen < shown.size() ? shown.get(chosen) : null;
		shown.clear();
		String query = what.strip().toLowerCase(Locale.ROOT);
		for (BookIO.Kept book : books) {
			if (query.isEmpty() || describe(book).toLowerCase(Locale.ROOT).contains(query)) {
				shown.add(book);
			}
		}
		// Starred books to the top, and among themselves in the order they were already in – the
		// sort is stable, so within each half the newest is still first. A shelf of two hundred
		// books is unusable without a handful of them being where they were last time.
		shown.sort((a, b) -> Boolean.compare(!starred(a), !starred(b)));
		// The book that was chosen stays chosen while it is still on show; otherwise the first, so
		// that the page beside the list is never blank for no reason.
		chosen = had == null ? (shown.isEmpty() ? -1 : 0) : shown.indexOf(had);
		if (chosen < 0 && !shown.isEmpty()) {
			chosen = 0;
		}
		reveal();
		deleteArmed = false;
		notice = null;
	}

	/** Keeps the chosen row on show without moving the list when it already is. */
	private void reveal() {
		int most = Math.max(0, shown.size() - SHOWN);
		if (chosen >= 0 && chosen < scroll) {
			scroll = chosen;
		} else if (chosen >= scroll + SHOWN) {
			scroll = chosen - SHOWN + 1;
		}
		scroll = MathHelper.clamp(scroll, 0, most);
	}

	/** Everything about a book that somebody might type looking for it. */
	private static String describe(BookIO.Kept book) {
		StringBuilder out = new StringBuilder(book.name());
		out.append(' ').append(book.author());
		for (String line : book.document().lore()) {
			out.append(' ').append(line);
		}
		for (Paragraph paragraph : book.document().page(0)) {
			out.append(' ').append(paragraph.text());
		}
		return out.toString();
	}

	@Nullable
	private BookIO.Kept current() {
		return chosen >= 0 && chosen < shown.size() ? shown.get(chosen) : null;
	}

	private static boolean starred(BookIO.Kept book) {
		return BookIO.isFavourite(book.document().id());
	}

	/**
	 * Stars a book or takes the star off, and puts the list back in order around it.
	 *
	 * <p>The row moves – to the top, or back down among the rest – because that is what starring a
	 * book is for. The list follows it there rather than staying where it was: a row that vanishes
	 * upwards the moment it is pressed looks like something has gone wrong, and the answer to
	 * "where did it go" should be on the screen rather than a scroll away.
	 */
	private void toggleStar(BookIO.Kept book) {
		BookIO.setFavourite(book.document().id(), !starred(book));
		filter(needle.getText());
		int now = shown.indexOf(book);
		if (now >= 0) {
			chosen = now;
			reveal();
		}
	}

	// ---- what the buttons do ----------------------------------------------------------------------

	private void read() {
		BookIO.Kept book = current();
		if (book != null) {
			MinecraftClient.getInstance().setScreen(new ShelfReadScreen(this, book, editor));
		}
	}

	private void restore() {
		BookIO.Kept book = current();
		if (book == null || editor == null) {
			return;
		}
		int dropped = restoreInto(editor, book.document());
		if (dropped > 0) {
			notice = Text.translatable("roleplayersquill.shelf.restored.partly", dropped)
					.formatted(Formatting.GOLD);
			return;
		}
		close();
	}

	/**
	 * Writes a kept book into the book being held, whole.
	 *
	 * <p>An ordinary edit, which is the point: it is one Ctrl+Z away from being undone, and nothing
	 * reaches the server until the book is written back in the usual way. The title comes with the
	 * pages – a recovered book that has to be renamed by hand is a book recovered by halves – but
	 * the author does not, because whoever signs this book next is the one who will have signed it.
	 *
	 * @return how many pages would not fit, which is none for any book that came off a real one
	 */
	static int restoreInto(PageEditor editor, QuillDocument source) {
		editor.document().mark();
		List<List<Paragraph>> pages = editor.document().pages();
		pages.clear();
		int fits = Math.min(source.pageCount(), QuillDocument.MAX_PAGES);
		for (int i = 0; i < fits; i++) {
			pages.add(QuillDocument.copyPage(source.page(i)));
		}
		if (pages.isEmpty()) {
			pages.add(QuillDocument.newPage());
		}
		if (!source.title().isBlank()) {
			editor.document().setTitle(source.title());
		}
		editor.setPage(0);
		editor.touch();
		return source.pageCount() - fits;
	}

	/**
	 * Asks what this book should be called, and calls it that.
	 *
	 * <p>The list is not rebuilt afterwards. The name is in the very document the row is drawn from,
	 * so the row says the new name at once, and a row that stayed where it was is the answer to
	 * "which one did I just rename".
	 */
	private void rename() {
		BookIO.Kept book = current();
		if (book == null) {
			return;
		}
		String was = book.document().title().isBlank() ? book.name() : book.document().title();
		MinecraftClient.getInstance().setScreen(new RenameScreen(this, was, typed -> {
			if (!BookIO.rename(book, typed)) {
				notice = Text.translatable("roleplayersquill.shelf.rename.failed").formatted(Formatting.RED);
			}
		}));
	}

	private void delete() {
		BookIO.Kept book = current();
		if (book == null) {
			return;
		}
		if (starred(book)) {
			// A starred book is not deleted by the button that deletes books. Saying so is the whole
			// of it: the way to remove one is to unstar it first, which is a press on the star that
			// is right there in the row, and having to mean it twice is the point.
			deleteArmed = false;
			if (deleteButton != null) {
				deleteButton.setMessage(deleteLabel());
			}
			notice = Text.translatable("roleplayersquill.shelf.delete.starred").formatted(Formatting.GOLD);
			return;
		}
		if (!deleteArmed) {
			deleteArmed = true;
			if (deleteButton != null) {
				deleteButton.setMessage(deleteLabel());
			}
			return;
		}
		deleteArmed = false;
		if (deleteButton != null) {
			deleteButton.setMessage(deleteLabel());
		}
		if (!BookIO.forget(book)) {
			notice = Text.translatable("roleplayersquill.shelf.delete.failed").formatted(Formatting.RED);
			return;
		}
		books.remove(book);
		filter(needle.getText());
		notice = Text.translatable("roleplayersquill.shelf.deleted").formatted(Formatting.GRAY);
	}

	private Text deleteLabel() {
		return deleteArmed
				? Text.translatable("roleplayersquill.shelf.delete.sure").formatted(Formatting.RED)
				: Text.translatable("roleplayersquill.shelf.delete");
	}

	// ---- input ------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int starRow = starAt(mouseX, mouseY);
		if (starRow >= 0) {
			// The star end of a row is the star and nothing else: a press there never opens the book,
			// however many times it is pressed.
			toggleStar(shown.get(starRow));
			return true;
		}
		int pencilRow = pencilAt(mouseX, mouseY);
		if (pencilRow >= 0) {
			chosen = pencilRow;
			rename();
			return true;
		}
		if (overBar(mouseX, mouseY)) {
			draggingBar = true;
			dragBarTo(mouseY);
			return true;
		}
		int row = rowAt(mouseX, mouseY);
		if (row >= 0) {
			if (row == chosen) {
				// A second click on the row already chosen opens it, the way a list opens a file
				// everywhere else.
				read();
				return true;
			}
			chosen = row;
			deleteArmed = false;
			if (deleteButton != null) {
				deleteButton.setMessage(deleteLabel());
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = MathHelper.clamp(scroll - (int) Math.signum(vertical), 0,
				Math.max(0, shown.size() - SHOWN));
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingBar) {
			dragBarTo(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingBar = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	// ---- the bar beside the list -------------------------------------------------------------------

	private int barTop() {
		return panelY + 48;
	}

	private int barHeight() {
		return SHOWN * ROW;
	}

	private int barX() {
		return panelX + 8 + LIST + 3;
	}

	private boolean overBar(double mouseX, double mouseY) {
		return shown.size() > SHOWN
				&& mouseX >= barX() - 2 && mouseX <= barX() + BAR + 2
				&& mouseY >= barTop() && mouseY <= barTop() + barHeight();
	}

	/**
	 * Where the grip has been dragged to, as a row to start the list at.
	 *
	 * <p>The grip is taken by its middle rather than by wherever it was grabbed, so that a press
	 * anywhere on the bar – above the grip, below it, on it – means "put the list here", which is
	 * what a press on a bar means in every list anybody has used.
	 */
	private void dragBarTo(double mouseY) {
		int most = Math.max(0, shown.size() - SHOWN);
		if (most == 0) {
			return;
		}
		int grip = gripHeight();
		double room = barHeight() - grip;
		double at = mouseY - barTop() - grip / 2.0;
		scroll = MathHelper.clamp((int) Math.round(at / room * most), 0, most);
	}

	private int gripHeight() {
		return Math.max(16, barHeight() * SHOWN / Math.max(1, shown.size()));
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_DOWN) {
			step(1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_UP) {
			step(-1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			read();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void step(int by) {
		if (shown.isEmpty()) {
			return;
		}
		chosen = MathHelper.clamp(chosen + by, 0, shown.size() - 1);
		if (chosen < scroll) {
			scroll = chosen;
		} else if (chosen >= scroll + SHOWN) {
			scroll = chosen - SHOWN + 1;
		}
		scroll = MathHelper.clamp(scroll, 0, Math.max(0, shown.size() - SHOWN));
		deleteArmed = false;
		if (deleteButton != null) {
			deleteButton.setMessage(deleteLabel());
		}
	}

	private int rowAt(double mouseX, double mouseY) {
		int top = panelY + 50;
		if (mouseX < panelX + 8 || mouseX > panelX + 8 + LIST) {
			return -1;
		}
		int row = (int) Math.floor((mouseY - top) / (double) ROW);
		if (row < 0 || row >= SHOWN) {
			return -1;
		}
		int index = scroll + row;
		return index < shown.size() ? index : -1;
	}

	/** Which row's star is under the cursor, of the rows that have one. */
	private int starAt(double mouseX, double mouseY) {
		int index = rowAt(mouseX, mouseY);
		return index >= 0 && mouseX >= starX() ? index : -1;
	}

	/** Which row's pencil is under the cursor: the star's neighbour, one place to the left. */
	private int pencilAt(double mouseX, double mouseY) {
		int index = rowAt(mouseX, mouseY);
		return index >= 0 && mouseX >= pencilX() && mouseX < starX() ? index : -1;
	}

	private int starX() {
		return panelX + 8 + LIST - STAR;
	}

	private int pencilX() {
		return starX() - STAR;
	}

	// ---- drawing ----------------------------------------------------------------------------------

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		boolean something = current() != null;
		if (readButton != null) {
			readButton.active = something;
		}
		if (restoreButton != null) {
			restoreButton.active = something;
		}
		if (deleteButton != null) {
			deleteButton.active = something;
		}
		super.render(context, mouseX, mouseY, delta);

		int top = panelY + 50;
		int hovered = rowAt(mouseX, mouseY);
		SimpleDateFormat stamp = new SimpleDateFormat("dd.MM.yyyy");
		for (int row = 0; row < SHOWN; row++) {
			int index = scroll + row;
			if (index >= shown.size()) {
				break;
			}
			BookIO.Kept book = shown.get(index);
			int y = top + row * ROW;
			if (index == chosen) {
				context.fill(panelX + 8, y - 2, panelX + 8 + LIST, y + ROW - 4, 0x60407090);
			}
			String name = book.name().isBlank()
					? Text.translatable("roleplayersquill.library.untitled").getString()
					: book.name();
			context.drawText(textRenderer, trim(name, LIST - 18 - STAR * 2),
					panelX + 12, y, book.signed() ? 0xFFE8D8A0 : 0xFFFFFFFF, false);

			StringBuilder about = new StringBuilder();
			if (!book.author().isBlank()) {
				about.append(book.author()).append(" · ");
			}
			about.append(stamp.format(new Date(book.when()))).append(" · ")
					.append(Text.translatable("roleplayersquill.history.pages",
							book.document().pageCount()).getString());
			context.drawText(textRenderer, trim(about.toString(), LIST - 18 - STAR * 2),
					panelX + 12, y + 10, 0xFF9A9A9A, false);

			// A hollow star and a pencil on every row rather than only on the one under the cursor:
			// a mark that appears when pointed at is a mark nobody knows is there until they point
			// at it. They light up under the cursor so that a press lands where it is meant to.
			boolean star = starred(book);
			boolean onStar = index == hovered && mouseX >= starX();
			boolean onPencil = index == hovered && mouseX >= pencilX() && mouseX < starX();
			context.drawText(textRenderer, "✎", pencilX() + 4, y + 5,
					onPencil ? 0xFFFFFFFF : 0xFF6A6A6A, false);
			context.drawText(textRenderer, star ? "★" : "☆", starX() + 3, y + 5,
					star ? 0xFFE8B23A : onStar ? 0xFFFFFFFF : 0xFF6A6A6A, false);
		}

		drawBar(context);

		if (shown.isEmpty()) {
			context.drawText(textRenderer,
					Text.translatable(books.isEmpty()
									? "roleplayersquill.shelf.empty" : "roleplayersquill.find.none")
							.formatted(Formatting.GRAY),
					panelX + 12, top + 4, 0xFFFFFFFF, false);
		}

		drawPreview(context);

		Text line = notice != null ? notice
				: Text.translatable("roleplayersquill.shelf.count", shown.size()).formatted(Formatting.GRAY);
		context.drawText(textRenderer, line, panelX + 12, panelY + panelHeight - 44, 0xFFFFFFFF, false);
	}

	/**
	 * The bar beside the list: how far down two hundred books this is, and the way to move.
	 *
	 * <p>Only drawn where there is something to scroll. A bar whose grip fills the whole track is a
	 * bar that says nothing and invites a drag that does nothing.
	 */
	private void drawBar(DrawContext context) {
		if (shown.size() <= SHOWN) {
			return;
		}
		int x = barX();
		int top = barTop();
		int height = barHeight();
		context.fill(x, top, x + BAR, top + height, 0x50000000);

		int grip = gripHeight();
		int most = shown.size() - SHOWN;
		int at = top + (height - grip) * scroll / most;
		context.fill(x, at, x + BAR, at + grip, draggingBar ? 0xFFD8C89A : 0xFF7A7A7A);
	}

	/**
	 * The first page of the chosen book, on parchment, laid out the way the book lays it out.
	 *
	 * <p>Laid out here rather than kept from disk, because a page is laid out against the font and
	 * the settings of whoever is looking at it. The parchment keeps the height of a whole page
	 * whether the book is chosen or not, so that walking the list does not make the frame jump.
	 */
	private void drawPreview(DrawContext context) {
		int x = panelX + 20 + LIST;
		int y = panelY + 50;
		int height = Layout.PAGE_LINES * Layout.LINE_HEIGHT;

		context.fill(x - 3, y - 3, x + (int) Layout.PAGE_WIDTH + 3, y + height + 3, 0xFFE9DBBF);
		context.drawBorder(x - 3, y - 3, (int) Layout.PAGE_WIDTH + 6, height + 6, 0xFF6B5A3E);

		BookIO.Kept book = current();
		if (book == null) {
			return;
		}
		List<Paragraph> page = book.document().page(0);
		List<Layout.LaidLine> lines = Layout.lay(page, QuillConfig.get().layoutOptions());
		for (int i = 0; i < Math.min(lines.size(), Layout.PAGE_LINES); i++) {
			BookPreview.drawLine(context, textRenderer, lines.get(i), page, x, y + i * Layout.LINE_HEIGHT);
		}

		// The lore under the page rather than on it: it was never part of the text, and a reader who
		// wants to know why one of four identical decrees is the sealed one is looking for exactly
		// the line the item carried.
		int loreY = y + height + 6;
		for (String lore : book.document().lore()) {
			if (loreY > panelY + panelHeight - 46) {
				break;
			}
			context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(lore,
					(int) Layout.PAGE_WIDTH + 6)), x - 3, loreY, 0xFF9A9A9A, false);
			loreY += 10;
		}
	}

	private String trim(String text, int width) {
		return textRenderer.getWidth(text) <= width ? text : textRenderer.trimToWidth(text, width - 6) + "…";
	}
}
