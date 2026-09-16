package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.config.QuillConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A row of symbols to click, and a button for all the rest.
 *
 * <p>The row is what has been used recently, filled out with a standard set – the quotation marks, the
 * dashes, the suits, the little decorations a roleplay chat actually uses. Clicking one puts it in;
 * the last button opens the full browser, where anything in the Basic Multilingual Plane can be
 * found by name.
 */
public final class SymbolBar {
	/**
	 * The order the shelves are walked in, most useful first.
	 *
	 * <p>The row holds the whole collection rather than a short list of favourites: the arrow walks
	 * through everything the browser has, which is where somebody already looking at the row expects
	 * it to take them. A dozen at a time, several hundred in all.
	 */
	private static final String[] ORDER = {
			Symbols.PACK, "punctuation", "shapes", "arrows", "items", "games", "nature", "signs",
			"brackets", "maths", "currency", "music", "frames", "greek", "indices", "runes", "latin"
	};

	private SymbolBar() {
	}

	/** How far along the list the row has been paged. Kept between openings of the chat. */
	private static int offset;

	/**
	 * The row, fixed for as long as the chat stays open.
	 *
	 * <p>It is built from what has been used recently and then left alone. Rebuilding it after every
	 * pick moved the symbol just clicked to the front and shuffled everything else along under the
	 * cursor, so the second of two clicks in the same place hit a different symbol. The order only
	 * changes between one opening of the chat and the next.
	 */
	@Nullable
	private static List<String> frozen;

	/** Called when the chat is opened afresh, so the row picks up what has been used since. */
	public static void reset() {
		frozen = null;
		offset = 0;
	}

	/**
	 * Builds the row: an arrow back, as many symbols as fit, an arrow on, and the full browser.
	 *
	 * @param room     how many pixels across the row may take
	 * @param insert   handed the symbol that was clicked
	 * @param relayout run when the row has to be built again, because paging changes what is in it
	 */
	public static List<IconButton> build(Screen owner, int room, Consumer<String> insert, Runnable relayout) {
		return build(owner, room, insert, relayout, null);
	}

	/**
	 * The same row, with a say in what the last button does.
	 *
	 * @param openFull what to do instead of opening the browser as a window of its own; a screen
	 *                 that can make room for the strip should keep itself on show and dock it
	 */
	public static List<IconButton> build(Screen owner, int room, Consumer<String> insert, Runnable relayout,
			@Nullable Runnable openFull) {
		List<IconButton> buttons = new ArrayList<>();
		int step = IconButton.SIZE + 1;
		// Three places are kept whatever else fits: the two arrows and the browser.
		int places = Math.max(1, room / step - 3);

		if (frozen == null) {
			frozen = shortlist();
		}
		List<String> all = frozen;
		if (offset >= all.size()) {
			offset = 0;
		}
		int page = places;

		// Back first, so that overshooting with the other arrow costs one press rather than a lap.
		buttons.add(new IconButton(0, 0, Icons.PAGE_LEFT, Text.translatable("roleplayersquill.symbols.back"), () -> {
			offset = offset - page < 0 ? Math.max(0, (all.size() - 1) / page * page) : offset - page;
			relayout.run();
		}));

		for (int i = offset; i < Math.min(offset + places, all.size()); i++) {
			String symbol = all.get(i);
			// No rebuild here on purpose: the row must stay exactly where it is so that clicking the
			// same spot twice puts the same symbol in twice.
			buttons.add(new IconButton(0, 0, Icons.of(symbol), Text.literal(symbol), () -> {
				insert.accept(symbol);
				QuillConfig.get().rememberSymbol(symbol);
			}));
		}

		// Both arrows wrap round rather than grey out, so the row is never a dead end.
		buttons.add(new IconButton(0, 0, Icons.PAGE_RIGHT, Text.translatable("roleplayersquill.symbols.on"), () -> {
			offset = offset + page >= all.size() ? 0 : offset + page;
			relayout.run();
		}));

		buttons.add(new IconButton(0, 0, Icons.SYMBOL, Text.translatable("roleplayersquill.tool.symbols"),
				openFull != null ? openFull
						: () -> MinecraftClient.getInstance().setScreen(new SymbolScreen(owner, symbol -> {
							insert.accept(symbol);
							QuillConfig.get().rememberSymbol(symbol);
						}))));
		return buttons;
	}

	/**
	 * Recently used first, then every shelf in turn, with no repeats.
	 *
	 * <p>The recent ones go through {@link Symbols#shelf} rather than straight out of the settings,
	 * so that a character the font has since stopped drawing – or one picked before there was any
	 * check at all – does not sit in the row as a blank button.
	 */
	private static List<String> shortlist() {
		Set<String> picked = new LinkedHashSet<>(Symbols.shelf(Symbols.RECENT));
		for (String shelf : ORDER) {
			picked.addAll(Symbols.shelf(shelf));
		}
		// The row draws what it holds straight into a button, so a resource pack's interface
		// pictures are left out of it entirely. They live on the shelves, where a square can stand
		// empty until the cursor is on it.
		return Symbols.onlySafe(new ArrayList<>(picked));
	}

	/** Lays the row out from left to right, starting at a point. */
	public static void place(List<IconButton> buttons, int x, int y) {
		int step = IconButton.SIZE + 1;
		for (IconButton button : buttons) {
			button.setX(x);
			button.setY(y);
			x += step;
		}
	}
}
