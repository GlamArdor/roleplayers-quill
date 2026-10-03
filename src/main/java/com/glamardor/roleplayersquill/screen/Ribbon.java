package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.Bookmarks;
import com.glamardor.roleplayersquill.config.QuillConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The bookmark, drawn hanging from the top of the book – the same in the editor, over a signed
 * book and in the page list.
 *
 * <p>Everything here is in the coordinates of the vanilla book texture: 192 by 192 with its top
 * left corner at nought, which is how the editor draws inside its scaled matrix and how the reader
 * is offset by the book's own left and top. The top margin of that texture is the cover from a y
 * of one down to seven, then paper; the text starts at thirty and the page number sits on the
 * right. The strip left of the number is the one place nothing is ever drawn, and that is where
 * the ribbon hangs.
 *
 * <p>Three states, and the whole of the interface is in them:
 * <ul>
 * <li>this page is marked: the ribbon hangs down to the text, and a click takes it out;</li>
 * <li>another page is: only its end shows over the cover, and a click turns to it – the way the
 * end of a ribbon sticks out of a closed book; the ghost of a whole ribbon under it offers to move
 * the mark here instead;</li>
 * <li>none is: nothing at all, until the pointer comes near and a ghost of one offers itself.</li>
 * </ul>
 *
 * <p>Never above the book. The ribbon starts on the cover's own top edge and darkens over its
 * first three rows, which is how a ribbon looks where it bends over the top of the pages and goes
 * down the other side: it was drawn sticking out over the edge once, and read as something stuck
 * on the screen rather than something in the book.
 *
 * <p>Drawn rather than painted, like {@link Icons}: a texture would have to be dyed ten colours,
 * cut five shapes and kept in step with resource packs, and this is a few rectangles.
 */
public final class Ribbon {
	public static final int X = 38;
	public static final int WIDTH = 7;
	/** The cover's top edge: the row where the ribbon bends over the pages to the far side. */
	public static final int TOP = 1;
	/** Where a ribbon in this very page ends: three pixels short of the first line of text. */
	public static final int LONG = 27;
	/** Where the end of a ribbon in another page ends: a few pixels onto the paper. */
	public static final int SHORT = 11;
	private static final int PAPER_TOP = 8;
	/** How dark each of the first rows is, where the ribbon bends away over the edge. */
	private static final int[] FOLD = { 0xA0, 0x68, 0x30 };

	private static final int GHOST = 0x70;

	private enum Zone {
		NONE, STUB, BODY
	}

	private Ribbon() {
	}

	private static Zone zone(BookView view, double x, double y) {
		if (!view.canBookmark() || x < X - 1 || x >= X + WIDTH + 1 || y < TOP - 1 || y >= LONG) {
			return Zone.NONE;
		}
		int mark = view.bookmark();
		return mark >= 0 && mark != view.page() && y < SHORT + 1 ? Zone.STUB : Zone.BODY;
	}

	/** Whether the pointer is on a bookmark actually drawn there – the one a right click dresses. */
	public static boolean overBookmark(BookView view, double x, double y) {
		Zone zone = zone(view, x, y);
		int mark = view.bookmark();
		return mark >= 0 && (zone == Zone.STUB || zone == Zone.BODY && mark == view.page());
	}

	/** Where the bookmark of the page on show ends, for something to hang under it. */
	public static int bottomOnBook(BookView view) {
		return view.bookmark() == view.page() ? LONG : SHORT;
	}

	/**
	 * Draws the bookmark of the page on show.
	 *
	 * @param pointer whether the pointer is the book's to answer – false under a dialog, where a
	 *                ghost offering a click that cannot happen would only be a lie
	 */
	public static void renderOnBook(DrawContext context, BookView view, double mouseX, double mouseY, boolean pointer) {
		if (!view.canBookmark()) {
			return;
		}
		int mark = view.bookmark();
		Bookmarks.Look look = view.bookmarkLook();
		Zone zone = pointer ? zone(view, mouseX, mouseY) : Zone.NONE;
		if (mark >= 0 && mark == view.page()) {
			shadow(context, look.styleOrDefault(), X, TOP, LONG);
			draw(context, X, TOP, LONG, 0xFF, zone != Zone.NONE, look);
			return;
		}
		if (zone == Zone.BODY) {
			draw(context, X, TOP, LONG, GHOST, false, look);
		}
		if (mark >= 0) {
			shadow(context, look.styleOrDefault(), X, TOP, SHORT);
			draw(context, X, TOP, SHORT, 0xFF, zone == Zone.STUB, look);
		}
	}

	/**
	 * A bookmark lies on the paper, so it throws a hairline of shadow on it to the right.
	 *
	 * <p>Cut from the shape itself rather than drawn per shape: the shape is put down once more into
	 * a grid of which pixels it covers, and every covered pixel on the paper with an uncovered one to
	 * its right gets the shadow there. So a pointed strap is shadowed along its point, the left one
	 * of two ribbons into the gap between them, and a cord and a feather all the way down – drawn by
	 * hand, each of those was missing or a pixel off somewhere.
	 */
	private static void shadow(DrawContext context, QuillConfig.BookmarkStyle style, int x, int top, int bottom) {
		boolean[][] solid = new boolean[bottom - top][WIDTH + 1];
		Fill mark = (x1, y1, x2, y2, colour) -> {
			for (int py = Math.max(y1, top); py < Math.min(y2, bottom); py++) {
				for (int px = Math.max(x1, x); px < Math.min(x2, x + WIDTH); px++) {
					solid[py - top][px - x] = true;
				}
			}
		};
		shape(mark, style, x, top, bottom, 0, 0, 0, 0);
		for (int y = Math.max(top, PAPER_TOP); y < bottom; y++) {
			boolean[] row = solid[y - top];
			for (int column = 0; column < WIDTH; column++) {
				if (row[column] && !row[column + 1]) {
					context.fill(x + column + 1, y, x + column + 2, y + 1, 0x30000000);
				}
			}
		}
	}

	/** What a click on the bookmark under the pointer would do, or null when it is not over one. */
	@Nullable
	public static Text tooltip(BookView view, double mouseX, double mouseY) {
		Zone zone = zone(view, mouseX, mouseY);
		if (zone == Zone.NONE) {
			return null;
		}
		int mark = view.bookmark();
		if (mark == view.page()) {
			return Text.translatable("roleplayersquill.bookmark.remove");
		}
		if (mark < 0) {
			return Text.translatable("roleplayersquill.bookmark.put");
		}
		return zone == Zone.STUB
				? Text.translatable("roleplayersquill.bookmark.go", mark + 1)
				: Text.translatable("roleplayersquill.bookmark.move", mark + 1);
	}

	/** Answers a click in the book's coordinates. True when it was the bookmark's to answer. */
	public static boolean click(BookView view, double mouseX, double mouseY) {
		Zone zone = zone(view, mouseX, mouseY);
		if (zone == Zone.NONE) {
			return false;
		}
		int mark = view.bookmark();
		if (mark == view.page()) {
			view.setBookmark(-1);
			playClick();
		} else if (zone == Zone.STUB) {
			view.setPage(mark);
			MinecraftClient.getInstance().getSoundManager().play(
					PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
		} else {
			view.setBookmark(view.page());
			playClick();
		}
		return true;
	}

	/** The small one in the page list: in that row or not, with the pointer over its spot or not. */
	public static void drawInList(DrawContext context, int x, int y, boolean marked, boolean hovered, boolean rowHovered,
			Bookmarks.Look look) {
		if (marked) {
			draw(context, x, y, y + 16, 0xFF, hovered, look);
		} else if (hovered || rowHovered) {
			draw(context, x, y, y + 16, hovered ? GHOST : GHOST / 2, false, look);
		}
	}

	public static void playClick() {
		MinecraftClient.getInstance().getSoundManager().play(
				PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	// ---- the shapes --------------------------------------------------------------------------

	public static void draw(DrawContext context, int x, int top, int bottom, int alpha, boolean lit, Bookmarks.Look look) {
		draw(context, x, top, bottom, alpha, lit, look.colourOrDefault(), look.styleOrDefault());
	}

	/**
	 * One bookmark from {@code top} down to {@code bottom}, {@link #WIDTH} wide.
	 *
	 * @param alpha 255 for the bookmark itself, less for the ghost of one
	 * @param lit   whether the pointer is on it, which brightens it the way a button brightens
	 */
	public static void draw(DrawContext context, int x, int top, int bottom, int alpha, boolean lit,
			QuillConfig.BookmarkColour colour, QuillConfig.BookmarkStyle style) {
		int rgb = colour.rgb;
		if (lit) {
			rgb = mix(rgb, 0xFFFFFF, 0.2f);
		}
		int base = (alpha << 24) | rgb;
		int dark = (alpha << 24) | mix(rgb, 0x000000, 0.38f);
		int light = (alpha << 24) | mix(rgb, 0xFFFFFF, 0.35f);
		int mid = (alpha << 24) | mix(rgb, 0x000000, 0.2f);
		shape(context::fill, style, x, top, bottom, base, dark, light, mid);
		// The bend over the edge, laid over exactly the columns the shape fills at the top – a shade
		// over the gap between two ribbons, or beside a cord, would darken the paper.
		switch (style) {
			case RIBBON, LEATHER -> fold(context, x, x + WIDTH, top, bottom, alpha);
			case TASSEL -> fold(context, x + 2, x + 5, top, bottom, alpha);
			case TWIN -> {
				fold(context, x, x + 3, top, bottom, alpha);
				fold(context, x + 4, x + WIDTH, top, bottom, alpha);
			}
			case FEATHER -> fold(context, x + 3, x + 4, top, bottom, alpha);
		}
	}

	/** Where a shape puts its rectangles: the screen, or the silhouette its shadow is cut from. */
	@FunctionalInterface
	private interface Fill {
		void fill(int x1, int y1, int x2, int y2, int colour);
	}

	private static void shape(Fill fill, QuillConfig.BookmarkStyle style, int x, int top, int bottom,
			int base, int dark, int light, int mid) {
		switch (style) {
			case RIBBON -> ribbon(fill, x, top, bottom, base, dark, light);
			case TASSEL -> tassel(fill, x, top, bottom, base, dark, light);
			case LEATHER -> leather(fill, x, top, bottom, base, dark, light);
			case TWIN -> twin(fill, x, top, bottom, base, dark, light);
			case FEATHER -> feather(fill, x, top, bottom, base, dark, light, mid);
		}
	}

	private static void fold(DrawContext context, int from, int to, int top, int bottom, int alpha) {
		for (int row = 0; row < FOLD.length && top + row < bottom - 3; row++) {
			context.fill(from, top + row, to, top + row + 1, (FOLD[row] * alpha / 255) << 24);
		}
	}

	/** Silk, with a highlight down one side and the end cut into a swallowtail. */
	private static void ribbon(Fill context, int x, int top, int bottom, int base, int dark, int light) {
		int body = bottom - 3;
		context.fill(x, top, x + WIDTH, body, base);
		context.fill(x + 1, top, x + 2, body, light);
		context.fill(x + 6, top, x + 7, body, dark);
		for (int r = 0; r < 3; r++) {
			int y = body + r;
			context.fill(x, y, x + 3 - r, y + 1, base);
			context.fill(x + 4 + r, y, x + 7, y + 1, base);
			if (r < 2) {
				context.fill(x + 1, y, x + 2, y + 1, light);
			}
			context.fill(x + 6, y, x + 7, y + 1, dark);
		}
	}

	/** A cord, a knot, and a tassel of threads with a ragged end. */
	private static void tassel(Fill context, int x, int top, int bottom, int base, int dark, int light) {
		int knot = Math.max(top, bottom - 8);
		context.fill(x + 2, top, x + 5, knot, base);
		context.fill(x + 4, top, x + 5, knot, dark);
		context.fill(x + 1, knot, x + 6, knot + 2, dark);
		context.fill(x + 2, knot, x + 4, knot + 1, base);
		context.fill(x + 1, knot + 2, x + 6, knot + 3, base);
		for (int column = 0; column < WIDTH; column++) {
			int end = column % 2 == 0 ? bottom - 1 : bottom;
			context.fill(x + column, knot + 3, x + column + 1, end, column % 2 == 0 ? base : light);
		}
	}

	/**
	 * A strip of leather with dark edges, a pointed end, and a row of small diamonds pressed into
	 * it – lit along their upper edges and shaded along their lower ones, the way a stamp leaves a
	 * hollow. A running stitch down the middle was tried first and looked like the line on a road.
	 */
	private static void leather(Fill context, int x, int top, int bottom, int base, int dark, int light) {
		int body = bottom - 3;
		context.fill(x, top, x + WIDTH, body, base);
		context.fill(x, top, x + 1, body, dark);
		context.fill(x + 6, top, x + 7, body, dark);
		for (int y = top + FOLD.length + 1; y + 3 <= body - 1; y += 5) {
			context.fill(x + 3, y, x + 4, y + 1, light);
			context.fill(x + 2, y + 1, x + 3, y + 2, light);
			context.fill(x + 4, y + 1, x + 5, y + 2, dark);
			context.fill(x + 3, y + 2, x + 4, y + 3, dark);
		}
		for (int r = 0; r < 3; r++) {
			int y = body + r;
			context.fill(x + r, y, x + WIDTH - r, y + 1, base);
			context.fill(x + r, y, x + r + 1, y + 1, dark);
			context.fill(x + WIDTH - r - 1, y, x + WIDTH - r, y + 1, dark);
		}
	}

	/**
	 * Two narrow ribbons side by side, as an old thick book has: the right one a shade lighter and
	 * shorter, so the two read as two rather than as one ribbon with a stripe down it.
	 */
	private static void twin(Fill context, int x, int top, int bottom, int base, int dark, int light) {
		int longEnd = bottom - 1;
		context.fill(x, top, x + 3, longEnd, base);
		context.fill(x + 2, top, x + 3, longEnd, dark);
		context.fill(x + 1, longEnd, x + 2, bottom, base);
		int shortEnd = bottom - 5;
		context.fill(x + 4, top, x + WIDTH, shortEnd, light);
		context.fill(x + 6, top, x + WIDTH, shortEnd, base);
		context.fill(x + 5, shortEnd, x + 6, shortEnd + 1, light);
	}

	/**
	 * A quill feather, the quill end over the edge of the pages: a bare light shaft first, then the
	 * vane, widening to the full width and tapering slowly to a point, its outer edge a shade darker
	 * and its barbs faint lines running out from the shaft. Barbs every third row in the full dark
	 * were tried first, and at seven pixels wide they read as a chessboard rather than a feather.
	 */
	private static void feather(Fill context, int x, int top, int bottom, int base, int dark, int light, int mid) {
		int shaft = x + 3;
		int vaneTop = top + 3;
		context.fill(shaft, top, shaft + 1, bottom - 1, light);
		for (int y = vaneTop; y < bottom; y++) {
			int row = y - vaneTop;
			int half = Math.min(3, Math.min(row + 1, (bottom - y + 1) / 2));
			for (int d = 1; d <= half; d++) {
				int colour = Math.floorMod(row - d, 4) == 0 ? mid : d == half ? dark : base;
				context.fill(shaft - d, y, shaft - d + 1, y + 1, colour);
				context.fill(shaft + d, y, shaft + d + 1, y + 1, colour);
			}
		}
	}

	private static int mix(int rgb, int with, float amount) {
		int r = Math.round(((rgb >> 16) & 255) * (1 - amount) + ((with >> 16) & 255) * amount);
		int g = Math.round(((rgb >> 8) & 255) * (1 - amount) + ((with >> 8) & 255) * amount);
		int b = Math.round((rgb & 255) * (1 - amount) + (with & 255) * amount);
		return (r << 16) | (g << 8) | b;
	}
}
