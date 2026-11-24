package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;

import com.glamardor.roleplayersquill.config.QuillConfig;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The character browser as a window, for the places that have no page to keep in view: the chat,
 * a sign, an anvil.
 *
 * <p>Inside the book editor the same shelves and the same search are shown by {@link SymbolPanel},
 * docked beside the page instead of over it, because there the whole point is watching what lands
 * where. Both read from {@link Symbols}.
 *
 * <p>Clicking inserts without closing: picking three symbols in a row should not mean opening this
 * three times.
 */
public class SymbolScreen extends DialogScreen {
	private static final int CELL = 18;
	// Sized to fit a 480 by 270 window, which is what a 1080p screen gives at the largest GUI scale.
	private static final int COLUMNS = 14;
	private static final int ROWS = 7;

	private static final int SEARCH_Y = 20;
	private static final int SHELF_Y = 40;
	private static final int GRID_Y = 60;

	private final Consumer<String> whenPicked;
	private EditBox search;
	private String shelf = Symbols.RECENT;
	private int scroll;
	private List<String> shown = List.of();

	public SymbolScreen(@Nullable Screen parent, Consumer<String> whenPicked) {
		super(parent, Component.translatable("roleplayersquill.symbols.title"));
		this.whenPicked = whenPicked;
		this.panelWidth = COLUMNS * CELL + 24;
		this.panelHeight = GRID_Y + ROWS * CELL + 48;
		Symbols.prepare();
	}

	@Override
	protected void init() {
		super.init();

		search = new EditBox(font, panelX + 12, panelY + SEARCH_Y, panelWidth - 24, 16,
				Component.translatable("roleplayersquill.symbols.search"));
		search.setMaxLength(48);
		search.setHint(Component.translatable("roleplayersquill.symbols.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setResponder(value -> {
			scroll = 0;
			refresh();
		});
		addRenderableWidget(search);

		// One shelf at a time with an arrow either side: nineteen shelves as buttons down the left
		// took more room than the symbols they were for.
		addRenderableWidget(Button.builder(Component.literal("◀"), button -> step(-1))
				.bounds(panelX + 12, panelY + SHELF_Y, 16, 16).build());
		addRenderableWidget(Button.builder(Component.literal("▶"), button -> step(1))
				.bounds(panelX + panelWidth - 28, panelY + SHELF_Y, 16, 16).build());

		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.symbols.close"), button -> onClose())
				.bounds(panelX + panelWidth / 2 - 42, panelY + panelHeight - 26, 84, 20).build());
		refresh();
	}

	/** Moves along the shelves, wrapping round at either end. */
	private void step(int delta) {
		List<String> names = Symbols.shelfNames();
		int at = Math.max(0, names.indexOf(shelf));
		shelf = names.get((at + delta + names.size()) % names.size());
		search.setValue("");
		scroll = 0;
		refresh();
	}

	private void refresh() {
		String query = search == null ? "" : search.getValue().trim();
		shown = query.isEmpty() ? Symbols.shelf(shelf) : Symbols.find(query, COLUMNS * 40);
	}

	private int gridX() {
		return panelX + 12;
	}

	private int gridY() {
		return panelY + GRID_Y;
	}

	private int indexAt(double mouseX, double mouseY) {
		// Math.floor, not a cast: a cast rounds towards zero, so a click just above or just left of
		// the grid landed on its first cell and took the click away from whatever was really there.
		int column = (int) Math.floor((mouseX - gridX()) / (double) CELL);
		int row = (int) Math.floor((mouseY - gridY()) / (double) CELL);
		if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
			return -1;
		}
		int index = (row + scroll) * COLUMNS + column;
		return index < shown.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		int button = Input.button(event);
		int index = indexAt(mouseX, mouseY);
		if (index >= 0) {
			String symbol = shown.get(index);
			whenPicked.accept(symbol);
			QuillConfig.get().rememberSymbol(symbol);
			// Left as it is on purpose: rebuilding would shuffle the recent shelf under the cursor.
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/**
	 * Up and down belong to whatever this was opened over.
	 *
	 * <p>On a sign they are how the line being typed into is changed, and a browser that swallows
	 * them turns filling in four lines into four rounds of open, pick, close, move, open again. The
	 * search box keeps them when it has the keyboard, since there they walk the results.
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key(), scanCode = event.keycode(), modifiers = event.modifiers();
		boolean arrow = keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN;
		if (arrow && parent != null && (search == null || !search.isFocused())) {
			return parent.keyPressed(event);
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
		scroll = Mth.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, rows - ROWS));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		Component shelfName = Component.translatable("roleplayersquill.symbols.shelf." + shelf);
		context.centeredText(font, shelfName,
				panelX + panelWidth / 2, panelY + SHELF_Y + 4, 0xFFD8D8D8);

		int x0 = gridX();
		int y0 = gridY();
		context.fill(x0, y0, x0 + COLUMNS * CELL, y0 + ROWS * CELL, 0x40000000);

		int hovered = indexAt(mouseX, mouseY);
		// Clipped to the grid, so that a character the font will not give a size for can at worst
		// spoil the grid instead of covering the screen behind it.
		context.enableScissor(x0, y0, x0 + COLUMNS * CELL, y0 + ROWS * CELL);
		for (int row = 0; row < ROWS; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				int index = (row + scroll) * COLUMNS + column;
				if (index >= shown.size()) {
					break;
				}
				int x = x0 + column * CELL;
				int y = y0 + row * CELL;
				if (index == hovered) {
					context.fill(x, y, x + CELL, y + CELL, 0x604C7BB0);
				}
				SymbolCell.draw(context, font, shown.get(index), x, y, CELL);
			}
		}
		context.disableScissor();

		if (shown.isEmpty()) {
			Component empty = !Symbols.indexed() && !search.getValue().isBlank()
					? Component.translatable("roleplayersquill.symbols.indexing")
					: Component.translatable("roleplayersquill.symbols.none");
			context.centeredText(font, empty.copy().withStyle(ChatFormatting.DARK_GRAY),
					x0 + COLUMNS * CELL / 2, y0 + ROWS * CELL / 2 - 4, 0xFFFFFFFF);
		}

		if (hovered >= 0) {
			context.text(font,
					Component.literal(Symbols.describe(shown.get(hovered))).withStyle(ChatFormatting.GRAY),
					x0, panelY + panelHeight - 20, 0xFFFFFFFF, false);
			SymbolCell.preview(context, font, shown.get(hovered), mouseX + 10, mouseY + 10,
					width, height);
		}
	}
}
