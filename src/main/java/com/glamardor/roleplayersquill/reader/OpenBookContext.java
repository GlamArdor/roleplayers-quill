package com.glamardor.roleplayersquill.reader;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The item a {@code BookScreen} is showing, held here for as long as the screen is open.
 *
 * <p>{@code BookScreen} only ever keeps the pages, not the stack they came from – {@code
 * Contents.create(ItemStack)} reads what it needs out of the stack and returns pages alone. So
 * {@link com.glamardor.roleplayersquill.mixin.BookScreenContentsMixin} sets this aside on the way
 * past that call, which is the one moment the stack and the screen it is about to open for are
 * both in hand at once. {@link SignedBook#infoFor} reads it back to put a name over a book read
 * out of a hand; a lectern never needs it; it keeps its own item on its handler.
 */
public final class OpenBookContext {
	@Nullable
	private static ItemStack current;

	private OpenBookContext() {
	}

	public static void stash(ItemStack stack) {
		current = stack;
	}

	@Nullable
	public static ItemStack peek() {
		return current;
	}
}
