package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.CodeToolbar;
import com.glamardor.roleplayersquill.screen.IconButton;
import com.glamardor.roleplayersquill.screen.SymbolBar;
import com.glamardor.roleplayersquill.screen.SymbolPanel;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.util.SelectionManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * A row of symbols on the sign editor.
 *
 * <p>Symbols rather than formatting, because a sign's colour and its glow come from dye and a glow
 * ink sac, not from anything a client can write into the text. What a sign does want is the same
 * thing a chat message wants: the quotation marks, the arrows, the little decorations, without
 * hunting for them somewhere else first.
 *
 * <p>Formatting codes are still available behind the same setting the chat uses, off by default.
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class SignEditScreenMixin extends Screen {
	@Shadow
	private SelectionManager selectionManager;

	@Shadow
	@Final
	private String[] messages;

	@Shadow
	@Final
	protected net.minecraft.block.entity.SignBlockEntity blockEntity;

	/** Whether the whole browser is showing under the sign. Survives the rebuild it causes. */
	@Unique
	private boolean roleplayersquill$symbolsOpen;

	protected SignEditScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void roleplayersquill$addToolbar(CallbackInfo ci) {
		QuillConfig config = QuillConfig.get();
		if (!config.signEditor) {
			return;
		}
		AbstractSignEditScreen owner = (AbstractSignEditScreen) (Object) this;
		java.util.function.Consumer<String> insert = text -> {
			if (selectionManager != null) {
				selectionManager.insert(text);
			}
		};

		// Just above the Done button, which vanilla puts at height/4 + 144.
		int y = this.height / 4 + 118;
		// Under the Done button, where there is usually room for the whole browser. When there is
		// not – a small window, or a large GUI scale – the button falls back to a window of its own.
		int stripTop = this.height / 4 + 170;
		int room = this.height - stripTop - 4;
		boolean canDock = room >= SymbolPanel.minimumHeight();

		List<IconButton> symbols = SymbolBar.build(owner, Math.min(this.width - 20, 320), insert,
				this::clearAndInit, canDock ? () -> {
					roleplayersquill$symbolsOpen = !roleplayersquill$symbolsOpen;
					clearAndInit();
				} : null);
		SymbolBar.place(symbols, this.width / 2 - symbols.size() * (IconButton.SIZE + 1) / 2, y);
		for (IconButton button : symbols) {
			addDrawableChild(button);
		}

		if (canDock && roleplayersquill$symbolsOpen) {
			int stripWidth = Math.min(SymbolPanel.WIDTH, this.width - 20);
			SymbolPanel panel = new SymbolPanel(insert);
			// The strip looks after its own drawing and its own clicks here. A sign editor declares
			// no mouseClicked of its own, so there is nothing to inject into to hand it the click.
			panel.drawItself();
			panel.layout(this.width / 2 - stripWidth / 2, stripTop, stripWidth, Math.min(room, 128),
					this::addDrawableChild, this.textRenderer);
		}

		if (config.chatFormatting) {
			List<IconButton> codes = CodeToolbar.build(owner, LegacyCodec.SECTION, insert);
			CodeToolbar.place(codes, this.width / 2, y - IconButton.SIZE - 1);
			for (IconButton button : codes) {
				addDrawableChild(button);
			}
		}
	}

	/**
	 * The spelling of what is on the sign, marked under the sign's own lettering.
	 *
	 * <p>Drawn at the end of the game's own text drawing, inside the same scaled matrix, and laid out
	 * by the same arithmetic: a line is centred, so it starts half its width to the left of the
	 * middle, and the four rows hang half a sign above and below it.
	 *
	 * <p>Marks and no menu. A sign holds four short lines and is usually a name on a shop, so the
	 * useful half of this is seeing that something is wrong; the whole line can be retyped in the
	 * time it takes to open anything.
	 */
	@Inject(method = "renderSignText", at = @At("TAIL"))
	private void roleplayersquill$spelling(net.minecraft.client.gui.DrawContext context, CallbackInfo ci) {
		if (messages == null || !com.glamardor.roleplayersquill.screen.SpellMarks.wanted()) {
			return;
		}
		int lineHeight = blockEntity.getTextLineHeight();
		int half = 4 * lineHeight / 2;
		for (int row = 0; row < messages.length; row++) {
			String line = messages[row];
			if (line == null || line.isBlank()) {
				continue;
			}
			int left = -this.textRenderer.getWidth(line) / 2;
			int top = row * lineHeight - half;
			com.glamardor.roleplayersquill.screen.SpellMarks.draw(context, line,
					index -> left + this.textRenderer.getWidth(line.substring(0, Math.min(index, line.length()))),
					top, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
		}
	}
}
