package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.config.QuillConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.Consumer;

/**
 * The character browser as a strip along the bottom of the editor.
 *
 * <p>It started as a window over the page, which hid the line the symbol was going into, and then
 * as a column down the left, which pushed the book and every button sideways whenever it opened.
 * Neither is what anybody wants while writing: the page has to stay exactly where it was and stay
 * visible. So it lies flat under the buttons, out of everything's way, and nothing moves.
 */
public final class SymbolPanel {
	private static final int CELL = 17;
	/** Room for the search box and the arrows, with a clear gap before the grid starts. */
	private static final int HEADER = 22;
	private static final int CAPTION = 11;
	private static final int SEARCH_WIDTH = 120;
	/** As wide as the row of buttons above it, which is what it is meant to line up under. */
	public static final int WIDTH = 308;

	private final Consumer<String> whenPicked;

	private TextFieldWidget search;
	private String shelf = Symbols.RECENT;
	private int scroll;
	private List<String> shown = List.of();

	private int x;
	private int y;
	private int width;
	private int height;
	private int columns;
	private int rows;

	public SymbolPanel(Consumer<String> whenPicked) {
		this.whenPicked = whenPicked;
		Symbols.prepare();
	}

	/** The least it is worth drawing at all: one row of symbols under its own header. */
	public static int minimumHeight() {
		return HEADER + CELL + CAPTION + 6;
	}

	/** Set before laying out, by hosts that cannot draw the strip or catch its clicks themselves. */
	private boolean selfDrawn;

	/**
	 * Makes the strip look after itself: its own drawing, its own clicks, its own wheel.
	 *
	 * <p>The book editor does all three by hand, because it has to decide what the page does with a
	 * click first. A screen reached through a mixin cannot – a sign editor does not so much as
	 * declare a {@code mouseClicked} to inject into – so for those the strip goes in as one more
	 * widget and the host does not have to know anything about it.
	 */
	public void drawItself() {
		this.selfDrawn = true;
	}

	/**
	 * The strip's drawing, as a widget that never takes the mouse.
	 *
	 * <p>It has to go in before the search box and the shelf arrows so that they are drawn over it
	 * rather than under it. A screen offers a click to its widgets in that same order, though, so
	 * one widget doing both would answer for the arrows sitting on top of it and they would never
	 * see a click at all. Hence two: this one draws and refuses the mouse, {@link Grid} below takes
	 * the mouse over the symbols and draws nothing.
	 */
	private final class Backdrop extends ClickableWidget {
		Backdrop() {
			super(SymbolPanel.this.x, SymbolPanel.this.y, SymbolPanel.this.width,
					SymbolPanel.this.height, Text.translatable("roleplayersquill.tool.symbols"));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			SymbolPanel.this.render(context, mouseX, mouseY);
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return false;
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return false;
		}

		@Override
		protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
		}
	}

	/** The symbols themselves: clicks and the wheel, over the grid and nowhere else. */
	private final class Grid extends ClickableWidget {
		Grid() {
			super(SymbolPanel.this.x, SymbolPanel.this.gridTop(), SymbolPanel.this.width,
					Math.max(1, SymbolPanel.this.height - HEADER),
					Text.translatable("roleplayersquill.tool.symbols"));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			SymbolPanel.this.mouseClicked(mouseX, mouseY, 0);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
			return SymbolPanel.this.mouseScrolled(vertical);
		}

		@Override
		protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
		}
	}

	/**
	 * Lays the strip out in the space it has been given.
	 *
	 * @param adder how the host screen takes a widget, since only it can
	 */
	public void layout(int x, int y, int width, int height, Consumer<ClickableWidget> adder,
			TextRenderer textRenderer) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = Math.max(minimumHeight(), height);
		this.columns = Math.max(1, (width - 8) / CELL);
		this.rows = Math.max(1, (this.height - HEADER - CAPTION) / CELL);

		// First, so that the search box and the arrows added below it are drawn over it rather than
		// under it: a screen draws its widgets in the order it was given them.
		if (selfDrawn) {
			adder.accept(new Backdrop());
		}

		search = new TextFieldWidget(textRenderer, x + 4, y + 3, SEARCH_WIDTH, 14,
				Text.translatable("roleplayersquill.symbols.search"));
		search.setMaxLength(48);
		search.setPlaceholder(Text.translatable("roleplayersquill.symbols.search").formatted(Formatting.DARK_GRAY));
		search.setChangedListener(value -> {
			scroll = 0;
			refresh();
		});
		adder.accept(search);

		// One shelf at a time with an arrow either side. There are seventeen of them; as buttons
		// they would fill the strip and leave no room for what is on them.
		adder.accept(ButtonWidget.builder(Text.literal("◀"), b -> step(-1))
				.dimensions(x + SEARCH_WIDTH + 8, y + 3, 14, 14).build());
		adder.accept(ButtonWidget.builder(Text.literal("▶"), b -> step(1))
				.dimensions(x + width - 18, y + 3, 14, 14).build());

		// Last, so that a click is offered to the arrows above it before it is offered to the grid.
		if (selfDrawn) {
			adder.accept(new Grid());
		}

		refresh();
	}

	private void step(int delta) {
		List<String> names = Symbols.shelfNames();
		int at = Math.max(0, names.indexOf(shelf));
		shelf = names.get((at + delta + names.size()) % names.size());
		if (search != null) {
			search.setText("");
		}
		scroll = 0;
		refresh();
	}

	/** True while the search box has the keyboard, so the host knows not to type into the book. */
	public boolean searching(Object focused) {
		return focused == search;
	}

	private void refresh() {
		String query = search == null ? "" : search.getText().trim();
		shown = query.isEmpty() ? Symbols.shelf(shelf) : Symbols.find(query, columns * 60);
	}

	public void reload() {
		refresh();
	}

	private int gridTop() {
		return y + HEADER;
	}

	private int indexAt(double mouseX, double mouseY) {
		// Math.floor, not a cast. A cast rounds towards zero, so a click one pixel above the grid
		// came out as row zero and the grid swallowed every click meant for the search box and the
		// arrows above it.
		int column = (int) Math.floor((mouseX - (x + 4)) / CELL);
		int row = (int) Math.floor((mouseY - gridTop()) / (double) CELL);
		if (column < 0 || column >= columns || row < 0 || row >= rows) {
			return -1;
		}
		int index = (row + scroll) * columns + column;
		return index < shown.size() ? index : -1;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int index = indexAt(mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		String symbol = shown.get(index);
		whenPicked.accept(symbol);
		QuillConfig.get().rememberSymbol(symbol);
		// The grid is deliberately left as it is. Rebuilding it here would move what was just
		// clicked to the front of the recent shelf and shuffle the rest under the cursor.
		return true;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	public boolean mouseScrolled(double vertical) {
		int lines = (shown.size() + columns - 1) / columns;
		scroll = MathHelper.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, lines - rows));
		return true;
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		context.fill(x, y, x + width, y + height, 0xE0181818);
		context.drawBorder(x, y, width, height, 0xFF000000);

		Text shelfName = Text.translatable("roleplayersquill.symbols.shelf." + shelf);
		int nameLeft = x + SEARCH_WIDTH + 24;
		int nameRight = x + width - 20;
		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal(textRenderer.trimToWidth(shelfName.getString(), nameRight - nameLeft)),
				(nameLeft + nameRight) / 2, y + 7, 0xFFE8D8A0);

		int gridX = x + 4;
		int gridTop = gridTop();
		context.fill(gridX, gridTop, gridX + columns * CELL, gridTop + rows * CELL, 0x40000000);

		int hovered = indexAt(mouseX, mouseY);
		// Clipped to the grid. The check that keeps a resource pack's screen-sized pictures out of
		// the squares depends on the font answering a question about them, and a font that will not
		// answer should cost a spoiled grid rather than a screen nobody can see past.
		context.enableScissor(gridX, gridTop, gridX + columns * CELL, gridTop + rows * CELL);
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				int index = (row + scroll) * columns + column;
				if (index >= shown.size()) {
					break;
				}
				int cellX = gridX + column * CELL;
				int cellY = gridTop + row * CELL;
				if (index == hovered) {
					context.fill(cellX, cellY, cellX + CELL, cellY + CELL, 0x604C7BB0);
				}
				SymbolCell.draw(context, textRenderer, shown.get(index), cellX, cellY, CELL);
			}
		}
		context.disableScissor();

		if (shown.isEmpty()) {
			Text empty = !Symbols.indexed() && search != null && !search.getText().isBlank()
					? Text.translatable("roleplayersquill.symbols.indexing")
					: Text.translatable("roleplayersquill.symbols.none");
			context.drawCenteredTextWithShadow(textRenderer, empty.copy().formatted(Formatting.DARK_GRAY),
					gridX + columns * CELL / 2, gridTop + rows * CELL / 2 - 4, 0xFFFFFFFF);
		}

		if (hovered >= 0) {
			context.drawText(textRenderer,
					Text.literal(textRenderer.trimToWidth(Symbols.describe(shown.get(hovered)), width - 12))
							.formatted(Formatting.GRAY),
					gridX, y + height - CAPTION, 0xFFFFFFFF, false);
			MinecraftClient client = MinecraftClient.getInstance();
			SymbolCell.preview(context, textRenderer, shown.get(hovered), mouseX + 10, mouseY + 10,
					client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
		}
	}
}
