package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.book.BookSender;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.CodeToolbar;
import com.glamardor.roleplayersquill.screen.IconButton;
import com.glamardor.roleplayersquill.screen.Icons;
import com.glamardor.roleplayersquill.screen.SymbolBar;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.ForgingScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

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
public abstract class AnvilScreenMixin extends ForgingScreen<AnvilScreenHandler> {
	@Shadow
	private TextFieldWidget nameField;

	/**
	 * The name typed so far, carried across a rebuild of the screen.
	 *
	 * <p>Paging the symbols along builds the screen again, and a fresh screen means a fresh name
	 * box filled from the handler rather than from what was being typed.
	 */
	@Unique
	private static String roleplayersquill$carried;

	protected AnvilScreenMixin(AnvilScreenHandler handler, net.minecraft.entity.player.PlayerInventory inventory,
			Text title, net.minecraft.util.Identifier texture) {
		super(handler, inventory, title, texture);
	}

	@Inject(method = "setup", at = @At("TAIL"))
	private void roleplayersquill$addToolbar(CallbackInfo ci) {
		QuillConfig config = QuillConfig.get();
		if (!config.anvilEditor) {
			return;
		}

		String carried = roleplayersquill$carried;
		roleplayersquill$carried = null;
		if (carried != null && nameField != null) {
			nameField.setText(carried);
		}

		AnvilScreen owner = (AnvilScreen) (Object) this;
		java.util.function.Consumer<String> insert = text -> {
			if (nameField == null) {
				return;
			}
			nameField.write(text);
			roleplayersquill$carried = nameField.getText();
			setFocused(nameField);
		};

		List<IconButton> symbols = SymbolBar.build(owner, this.backgroundWidth, insert, () -> {
			if (nameField != null) {
				roleplayersquill$carried = nameField.getText();
			}
			clearAndInit();
		});
		SymbolBar.place(symbols, this.x, this.y - 22);
		for (IconButton button : symbols) {
			addDrawableChild(button);
		}

		if (config.chatFormatting) {
			char prefix = config.chatCodePrefix.charAt(0);
			List<IconButton> codes = CodeToolbar.build(owner, prefix, insert);
			CodeToolbar.place(codes, this.x + this.backgroundWidth / 2, this.y - 42);
			for (IconButton button : codes) {
				addDrawableChild(button);
			}
		}

		// Only offered where it can work at all. Outside creative the server will not take a finished
		// item, and a button that is greyed out for reasons the player cannot see is worse than one
		// that is simply not there.
		if (BookSender.canWriteRich()) {
			IconButton creative = new IconButton(0, 0, Icons.COLOR_SWATCH,
					Text.translatable("roleplayersquill.anvil.creative"), this::roleplayersquill$giveNamed);
			creative.setX(this.x + this.backgroundWidth + 4);
			creative.setY(this.y - 22);
			addDrawableChild(creative);
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
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || nameField == null) {
			return;
		}
		ItemStack source = this.handler.getSlot(0).getStack();
		if (source.isEmpty()) {
			return;
		}
		String typed = nameField.getText();
		if (typed.isBlank()) {
			return;
		}
		char prefix = QuillConfig.get().chatCodePrefix.charAt(0);
		String resolved = prefix == LegacyCodec.SECTION
				? typed
				: CodeToolbar.preview(typed, prefix).getString();

		ItemStack copy = source.copy();
		copy.set(DataComponentTypes.CUSTOM_NAME, Text.literal(resolved));
		BookSender.setHeldItem(copy, Hand.MAIN_HAND);
	}
}
