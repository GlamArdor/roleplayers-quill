package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.QuillEditScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Opens this mod's editor instead of the vanilla one when a writable book is used.
 *
 * <p>One method, and the one the game itself calls when the book is opened, rather than a guess at
 * which screen is about to appear. With the setting off nothing here fires at all and the vanilla
 * editor opens exactly as it always did.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	@Inject(method = "useBook(Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;)V",
			at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$openEditor(ItemStack book, Hand hand, CallbackInfo ci) {
		if (!QuillConfig.get().replaceBookEditor) {
			return;
		}
		// Sneaking opens the vanilla editor instead. Other mods add to that screen – ImagineBook
		// puts pictures on a page through it – and taking it away entirely would mean this mod
		// quietly removing a feature it knows nothing about. One modifier key keeps both.
		if (net.minecraft.client.gui.screen.Screen.hasShiftDown()) {
			return;
		}
		WritableBookContentComponent content = book.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
		if (content == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		List<String> pages = content.stream(client.shouldFilterText()).toList();
		client.setScreen(new QuillEditScreen(book, hand, pages));
		ci.cancel();
	}
}
