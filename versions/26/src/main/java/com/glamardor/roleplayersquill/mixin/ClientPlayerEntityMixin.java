package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.QuillEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;

/**
 * Opens this mod's editor instead of the vanilla one when a writable book is used.
 *
 * <p>One method, and the one the game itself calls when the book is opened, rather than a guess at
 * which screen is about to appear. With the setting off nothing here fires at all and the vanilla
 * editor opens exactly as it always did.
 */
@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin {
	@Inject(method = "openItemGui(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)V",
			at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$openEditor(ItemStack book, InteractionHand hand, CallbackInfo ci) {
		if (!QuillConfig.get().replaceBookEditor) {
			return;
		}
		// Sneaking opens the vanilla editor instead. Other mods add to that screen – ImagineBook
		// puts pictures on a page through it – and taking it away entirely would mean this mod
		// quietly removing a feature it knows nothing about. One modifier key keeps both.
		if (Minecraft.getInstance().hasShiftDown()) {
			return;
		}
		WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (content == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		List<String> pages = content.getPages(client.isTextFilteringEnabled()).toList();
		client.gui.setScreen(new QuillEditScreen(book, hand, pages));
		ci.cancel();
	}
}
