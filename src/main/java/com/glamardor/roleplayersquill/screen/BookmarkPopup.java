package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.Bookmarks;
import com.glamardor.roleplayersquill.config.QuillConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * What a right click on the bookmark opens: its colour and its shape, picked in the book itself.
 *
 * <p>One book's bookmark, not every book's – the settings say what a new bookmark looks like, and
 * this dresses the one in hand, so a book of laws can wear red and a diary blue. The last cell puts
 * it back to following the settings.
 *
 * <p>Shared by the editor and the reader, the way {@link Ribbon} is: both hand it the screen
 * position of the bookmark, and it does the rest in screen coordinates.
 */
public final class BookmarkPopup {
	private static final int PAD = 6;
	private static final int SWATCH = 12;
	private static final int COLOURS = QuillConfig.BookmarkColour.values().length;
	private static final int WIDTH = PAD * 2 + COLOURS * (SWATCH + 1) - 1;
	private static final int COLOUR_TOP = 16;
	private static final int STYLE_TOP = COLOUR_TOP + SWATCH + 5;
	private static final int STYLE_CELL = 20;
	/** What {@link #styleAt} answers over the cell that puts the bookmark back to the settings. */
	private static final int RESET = -2;
	private static final int STYLE_HEIGHT = 24;
	private static final int HEIGHT = STYLE_TOP + STYLE_HEIGHT + PAD;

	private final BookView view;
	private int x;
	private int y;

	public BookmarkPopup(BookView view) {
		this.view = view;
	}

	/** Under the bookmark, kept on the screen. */
	public void layout(int anchorX, int anchorY, int screenWidth, int screenHeight) {
		x = Math.max(2, Math.min(anchorX - PAD, screenWidth - WIDTH - 2));
		y = Math.max(2, Math.min(anchorY + 3, screenHeight - HEIGHT - 2));
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + HEIGHT;
	}

	private int colourAt(double mouseX, double mouseY) {
		if (mouseY < y + COLOUR_TOP || mouseY >= y + COLOUR_TOP + SWATCH || mouseX < x + PAD) {
			return -1;
		}
		int index = (int) Math.floor((mouseX - x - PAD) / (SWATCH + 1));
		return index < COLOURS ? index : -1;
	}

	/** The index of a shape, {@link #RESET} for "as the settings say", -1 for neither. */
	private int styleAt(double mouseX, double mouseY) {
		if (mouseY < y + STYLE_TOP || mouseY >= y + STYLE_TOP + STYLE_HEIGHT || mouseX < x + PAD) {
			return -1;
		}
		int index = (int) Math.floor((mouseX - x - PAD) / STYLE_CELL);
		if (index < QuillConfig.BookmarkStyle.values().length) {
			return index;
		}
		return mouseX >= resetX() && mouseX < x + WIDTH - PAD ? RESET : -1;
	}

	private int resetX() {
		return x + PAD + QuillConfig.BookmarkStyle.values().length * STYLE_CELL + 4;
	}

	public boolean pickAt(double mouseX, double mouseY) {
		Bookmarks.Look look = view.bookmarkLook();
		int colour = colourAt(mouseX, mouseY);
		if (colour >= 0) {
			view.setBookmarkLook(new Bookmarks.Look(QuillConfig.BookmarkColour.values()[colour], look.style()));
			Ribbon.playClick();
			return true;
		}
		int style = styleAt(mouseX, mouseY);
		if (style == RESET) {
			view.setBookmarkLook(Bookmarks.Look.DEFAULT);
			Ribbon.playClick();
			return true;
		}
		if (style >= 0) {
			view.setBookmarkLook(new Bookmarks.Look(look.colour(), QuillConfig.BookmarkStyle.values()[style]));
			Ribbon.playClick();
			return true;
		}
		// Anywhere else inside the panel is the panel's, not the page's under it.
		return contains(mouseX, mouseY);
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		Bookmarks.Look look = view.bookmarkLook();
		QuillConfig.BookmarkColour colourNow = look.colourOrDefault();
		QuillConfig.BookmarkStyle styleNow = look.styleOrDefault();

		context.fill(x, y, x + WIDTH, y + HEIGHT, 0xF0181818);
		context.drawBorder(x, y, WIDTH, HEIGHT, 0xFF000000);
		context.drawText(textRenderer, Text.translatable("roleplayersquill.bookmark.popup"),
				x + PAD, y + 4, 0xFFE8D8A0, false);

		int hoveredColour = colourAt(mouseX, mouseY);
		QuillConfig.BookmarkColour[] colours = QuillConfig.BookmarkColour.values();
		for (int i = 0; i < colours.length; i++) {
			int left = x + PAD + i * (SWATCH + 1);
			int top = y + COLOUR_TOP;
			if (colours[i] == colourNow) {
				context.fill(left - 1, top - 1, left + SWATCH + 1, top + SWATCH + 1, 0xFFFFFFFF);
			} else if (i == hoveredColour) {
				context.fill(left - 1, top - 1, left + SWATCH + 1, top + SWATCH + 1, 0xFF808080);
			}
			context.fill(left, top, left + SWATCH, top + SWATCH, 0xFF000000 | colours[i].rgb);
		}

		int hoveredStyle = styleAt(mouseX, mouseY);
		QuillConfig.BookmarkStyle[] styles = QuillConfig.BookmarkStyle.values();
		for (int i = 0; i < styles.length; i++) {
			int left = x + PAD + i * STYLE_CELL;
			int top = y + STYLE_TOP;
			int background = styles[i] == styleNow ? 0x804C7BB0 : i == hoveredStyle ? 0x40FFFFFF : 0x30FFFFFF;
			context.fill(left, top, left + STYLE_CELL - 2, top + STYLE_HEIGHT, background);
			// Drawn on a scrap of page, so a gold or a pink ribbon is seen the way it will be seen.
			context.fill(left + 3, top + 2, left + STYLE_CELL - 5, top + STYLE_HEIGHT - 2, 0xFFF3E9D2);
			Ribbon.draw(context, left + (STYLE_CELL - 2 - Ribbon.WIDTH) / 2, top + 2, top + STYLE_HEIGHT - 2,
					0xFF, false, colourNow, styles[i]);
		}

		boolean inherits = look.colour() == null && look.style() == null;
		int resetLeft = resetX();
		int top = y + STYLE_TOP;
		context.fill(resetLeft, top, x + WIDTH - PAD, top + STYLE_HEIGHT,
				inherits ? 0x804C7BB0 : hoveredStyle == RESET ? 0x40FFFFFF : 0x30FFFFFF);
		Text reset = Text.literal("↺");
		context.drawText(textRenderer, reset,
				(resetLeft + x + WIDTH - PAD - textRenderer.getWidth(reset)) / 2, top + 8, 0xFFE0E0E0, false);

		Text tip = tipAt(mouseX, mouseY, hoveredColour, hoveredStyle);
		if (tip != null) {
			context.drawTooltip(textRenderer, tip, mouseX, mouseY);
		}
	}

	@Nullable
	private static Text tipAt(int mouseX, int mouseY, int colour, int style) {
		if (colour >= 0) {
			return QuillConfig.BookmarkColour.values()[colour].label();
		}
		if (style == RESET) {
			return Text.translatable("roleplayersquill.bookmark.popup.reset");
		}
		if (style >= 0) {
			return QuillConfig.BookmarkStyle.values()[style].label();
		}
		return null;
	}
}
