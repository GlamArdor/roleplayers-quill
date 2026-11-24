package com.glamardor.roleplayersquill.mixin;

import net.minecraft.client.Minecraft;

import com.glamardor.roleplayersquill.reader.ReadHost;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shift on the arrows of a book on a lectern: to the first page or the last one.
 *
 * <p>The lectern turns its pages through the server, so it overrides both arrows without calling
 * {@code BookScreen}'s, and {@link BookScreenMixin} never hears of them. {@code jumpToPage} here is
 * the lectern's own, which asks the server for the page – everybody reading along sees the jump,
 * exactly as they see every other turn of a lectern's pages.
 */
@Mixin(LecternScreen.class)
public abstract class LecternScreenMixin extends BookViewScreen {
	protected LecternScreenMixin() {
		super();
	}

	@Inject(method = "pageBack", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$toFirstPage(CallbackInfo ci) {
		if (Minecraft.getInstance().hasShiftDown()) {
			this.forcePage(0);
			ci.cancel();
		}
	}

	@Inject(method = "pageForward", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$toLastPage(CallbackInfo ci) {
		if (Minecraft.getInstance().hasShiftDown()) {
			this.forcePage(((ReadHost) (Object) this).contents().getPageCount() - 1);
			ci.cancel();
		}
	}
}
