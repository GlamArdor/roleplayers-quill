package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.gui.LivePreview;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the world – and the editor – visible behind this mod's settings.
 *
 * <p>Half of these settings change how a page is laid out, and the page is sitting right there
 * behind the menu. A blurred, darkened background would hide the one thing worth looking at.
 *
 * <p>Only for our own screen: every other menu in the game and in every other mod keeps its
 * background exactly as it was.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	/*
	 * Both injections are require = 0 on purpose.
	 *
	 * This is decoration. If a future version of the game renames or removes either method, the
	 * mixin quietly does nothing and the background goes back to being blurred, which is a cosmetic
	 * regression. The alternative, and the default, is that the mixin fails to apply and the game
	 * refuses to start. Nothing that only affects how a menu looks should be able to do that.
	 */
	@Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true, require = 0)
	private void roleplayersquill$noBlur(DrawContext context, CallbackInfo ci) {
		if (LivePreview.isOurScreenOpen()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void roleplayersquill$noDarkening(DrawContext context, CallbackInfo ci) {
		if (LivePreview.isOurScreenOpen()) {
			ci.cancel();
		}
	}
}
