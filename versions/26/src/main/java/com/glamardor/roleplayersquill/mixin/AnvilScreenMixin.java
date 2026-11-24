package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.book.BookSender;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.CodeToolbar;
import com.glamardor.roleplayersquill.screen.IconButton;
import com.glamardor.roleplayersquill.screen.Icons;
import com.glamardor.roleplayersquill.screen.SymbolBar;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;

/**
 * A row of symbols on the anvil, for the same reason the sign has one: naming a sword «Клинок ⚔»
 * should not mean going somewhere else to find the sword.
 *
 * <p>Formatting is a different matter here and a poor one. The server runs the typed name through
 * {@code StringHelper.stripInvalidChars}, and that method exists specifically to remove section
 * signs – so a code typed here only reaches the server where a plugin is reading some other
 * character instead. Those buttons are therefore behind the same setting the chat uses, off by
 * default.
 *
 * <p>In creative there is a way round it, and it is offered as its own button rather than hidden
 * inside the rename: the client may hand the server a finished item, so a copy of the item with a
 * properly coloured name can be put straight into the player's hand.
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> {
	@Shadow
	private EditBox name;

	/**
	 * The name typed so far, carried across a rebuild of the screen.
	 *
	 * <p>Paging the symbols along builds the screen again, and a fresh screen means a fresh name
	 * box filled from the handler rather than from what was being typed.
	 */
	@Unique
	private static String roleplayersquill$carried;

	protected AnvilScreenMixin(AnvilMenu handler, net.minecraft.world.entity.player.Inventory inventory,
			Component title, net.minecraft.resources.Identifier texture) {
		super(handler, inventory, title, texture);
	}

	@Inject(method = "subInit", at = @At("TAIL"))
	private void roleplayersquill$addToolbar(CallbackInfo ci) {
		QuillConfig config = QuillConfig.get();
		if (!config.anvilEditor) {
			return;
		}

		String carried = roleplayersquill$carried;
		roleplayersquill$carried = null;
		if (carried != null && name != null) {
			name.setValue(carried);
		}

		AnvilScreen owner = (AnvilScreen) (Object) this;
		java.util.function.Consumer<String> insert = text -> {
			if (name == null) {
				return;
			}
			name.insertText(text);
			roleplayersquill$carried = name.getValue();
			setFocused(name);
		};

		List<IconButton> symbols = SymbolBar.build(owner, this.imageWidth, insert, () -> {
			if (name != null) {
				roleplayersquill$carried = name.getValue();
			}
			rebuildWidgets();
		});
		SymbolBar.place(symbols, this.leftPos, this.topPos - 22);
		for (IconButton button : symbols) {
			addRenderableWidget(button);
		}

		if (config.chatFormatting) {
			char prefix = config.chatCodePrefix.charAt(0);
			List<IconButton> codes = CodeToolbar.build(owner, prefix, insert);
			CodeToolbar.place(codes, this.leftPos + this.imageWidth / 2, this.topPos - 42);
			for (IconButton button : codes) {
				addRenderableWidget(button);
			}
		}

		// Only offered where it can work at all. Outside creative the server will not take a finished
		// item, and a button that is greyed out for reasons the player cannot see is worse than one
		// that is simply not there.
		if (BookSender.canWriteRich()) {
			IconButton creative = new IconButton(0, 0, Icons.COLOR_SWATCH,
					Component.translatable("roleplayersquill.anvil.creative"), this::roleplayersquill$giveNamed);
			creative.setX(this.leftPos + this.imageWidth + 4);
			creative.setY(this.topPos - 22);
			addRenderableWidget(creative);
		}
	}

	/**
	 * Puts a copy of the item being renamed into the player's hand, with the name as a component.
	 *
	 * <p>The anvil's own slots are the server's to fill, so the copy goes where a creative client is
	 * allowed to put things: its own inventory.
	 */
	@Unique
	private void roleplayersquill$giveNamed() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || name == null) {
			return;
		}
		ItemStack source = this.menu.getSlot(0).getItem();
		if (source.isEmpty()) {
			return;
		}
		String typed = name.getValue();
		if (typed.isBlank()) {
			return;
		}
		char prefix = QuillConfig.get().chatCodePrefix.charAt(0);
		String resolved = prefix == LegacyCodec.SECTION
				? typed
				: CodeToolbar.preview(typed, prefix).getString();

		ItemStack copy = source.copy();
		copy.set(DataComponents.CUSTOM_NAME, Component.literal(resolved));
		BookSender.setHeldItem(copy, InteractionHand.MAIN_HAND);
	}
}
