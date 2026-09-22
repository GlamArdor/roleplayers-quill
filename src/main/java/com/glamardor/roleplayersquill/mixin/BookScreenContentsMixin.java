package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.reader.LinkDetector;
import com.glamardor.roleplayersquill.reader.OpenBookContext;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hands the reader a page with its addresses made clickable.
 *
 * <p>Here rather than in the render method on purpose: the reader asks for the page once and caches
 * both the wrapped lines and the styles it will test a click against, so decorating the page on the
 * way out means the hover and the click both work with nothing else touched.
 */
@Mixin(BookScreen.Contents.class)
public class BookScreenContentsMixin {
	@Inject(method = "getPage", at = @At("RETURN"), cancellable = true)
	private void roleplayersquill$linkify(int index, CallbackInfoReturnable<Text> info) {
		Text page = info.getReturnValue();
		if (page != null) {
			info.setReturnValue(LinkDetector.decorate(page));
		}
	}

	/**
	 * {@code Contents} only ever keeps the pages it was built from, not the stack – so the stack is
	 * set aside here, the one place it and the screen about to open for it are both still in hand,
	 * for {@link com.glamardor.roleplayersquill.reader.SignedBook} to put a name over the book with.
	 */
	@Inject(method = "create", at = @At("HEAD"))
	private static void roleplayersquill$rememberStack(ItemStack stack, CallbackInfoReturnable<BookScreen.Contents> info) {
		OpenBookContext.stash(stack);
	}
}
