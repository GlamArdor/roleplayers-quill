package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.CodeToolbar;
import com.glamardor.roleplayersquill.screen.IconButton;
import com.glamardor.roleplayersquill.screen.SymbolBar;
import com.glamardor.roleplayersquill.screen.SymbolPanel;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;

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
	private TextFieldHelper signField;

	@Shadow
	@Final
	private String[] messages;

	@Shadow
	@Final
	protected net.minecraft.world.level.block.entity.SignBlockEntity sign;

	@Shadow
	private int line;

	@Shadow
	protected abstract float getSignYOffset();

	@Shadow
	private void setMessage(String message) {
	}

	/** Whether the whole browser is showing under the sign. Survives the rebuild it causes. */
	@Unique
	private boolean roleplayersquill$symbolsOpen;

	protected SignEditScreenMixin(Component title) {
		super(title);
	}

	/** Every widget this mod put on the screen, so that they can be taken off again on their own. */
	@Unique
	private final List<net.minecraft.client.gui.components.events.GuiEventListener> roleplayersquill$ours = new java.util.ArrayList<>();

	@Inject(method = "init", at = @At("TAIL"))
	private void roleplayersquill$addToolbar(CallbackInfo ci) {
		roleplayersquill$ours.clear();
		roleplayersquill$build();
	}

	/**
	 * Builds this mod's widgets again and nothing else.
	 *
	 * <p>Paging the row of symbols, or opening the strip under the sign, used to rebuild the whole
	 * screen. That runs the sign editor's own init again, and other mods hook it: Litematica, with
	 * its sign text paste on, writes the text of the sign in a loaded schematic over all four rows
	 * every time it runs – and an empty sign in the schematic wipes the real one.
	 */
	@Unique
	private void roleplayersquill$rebuild() {
		for (net.minecraft.client.gui.components.events.GuiEventListener element : roleplayersquill$ours) {
			removeWidget(element);
		}
		roleplayersquill$ours.clear();
		setFocused(null);
		roleplayersquill$build();
	}

	@Unique
	private <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable
			& net.minecraft.client.gui.narration.NarratableEntry> void roleplayersquill$add(T widget) {
		addRenderableWidget(widget);
		roleplayersquill$ours.add(widget);
	}

	@Unique
	private void roleplayersquill$build() {
		QuillConfig config = QuillConfig.get();
		if (!config.signEditor) {
			return;
		}
		AbstractSignEditScreen owner = (AbstractSignEditScreen) (Object) this;
		java.util.function.Consumer<String> insert = text -> {
			if (signField != null) {
				signField.insertText(text);
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
				this::roleplayersquill$rebuild, canDock ? () -> {
					roleplayersquill$symbolsOpen = !roleplayersquill$symbolsOpen;
					roleplayersquill$rebuild();
				} : null);
		SymbolBar.place(symbols, this.width / 2 - symbols.size() * (IconButton.SIZE + 1) / 2, y);
		for (IconButton button : symbols) {
			roleplayersquill$add(button);
		}

		if (canDock && roleplayersquill$symbolsOpen) {
			int stripWidth = Math.min(SymbolPanel.WIDTH, this.width - 20);
			SymbolPanel panel = new SymbolPanel(insert);
			// The strip looks after its own drawing and its own clicks here. A sign editor declares
			// no mouseClicked of its own, so there is nothing to inject into to hand it the click.
			panel.drawItself();
			panel.layout(this.width / 2 - stripWidth / 2, stripTop, stripWidth, Math.min(room, 128),
					this::roleplayersquill$add, this.font);
		}

		roleplayersquill$addClipboard();

		if (config.chatFormatting) {
			List<IconButton> codes = CodeToolbar.build(owner, LegacyCodec.SECTION, insert);
			CodeToolbar.place(codes, this.width / 2, y - IconButton.SIZE - 1);
			for (IconButton button : codes) {
				roleplayersquill$add(button);
			}
		}
	}

	/**
	 * Keeps the sign open on the server while one of this mod's windows is over it.
	 *
	 * <p>The game sends a sign's text the moment its editor is replaced, and the server takes that as
	 * the end of the edit: it saves the text and stops letting this player change the sign. So a
	 * symbol picked in the browser, and everything typed after coming back, was refused when the sign
	 * was finished. The text now goes when the editor really closes, with everything in it.
	 */
	@Inject(method = "removed", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$keepEditing(CallbackInfo ci) {
		if (com.glamardor.roleplayersquill.screen.Detour.isLeaving(this)) {
			ci.cancel();
		}
	}

	/**
	 * Copy, paste and clear, in a column to the left of the sign, as Stendhal has them.
	 *
	 * <p>Through the system clipboard, one row to a line, so a sign can be copied onto another sign
	 * or written somewhere else first and pasted in whole. Pasting replaces all four rows: a row the
	 * clipboard has no line for is emptied, and a line too wide for the sign is cut where the sign
	 * would have stopped taking letters.
	 */
	@Unique
	private void roleplayersquill$addClipboard() {
		java.util.function.BooleanSupplier written = () -> {
			for (String line : messages) {
				if (line != null && !line.isEmpty()) {
					return true;
				}
			}
			return false;
		};
		List<IconButton> buttons = List.of(
				new IconButton(0, 0, com.glamardor.roleplayersquill.screen.Icons.PAGE_COPY,
						Component.translatable("roleplayersquill.tool.sign_copy"), this::roleplayersquill$copy)
						.onlyWhen(written),
				new IconButton(0, 0, com.glamardor.roleplayersquill.screen.Icons.PAGE_PASTE,
						Component.translatable("roleplayersquill.tool.sign_paste"), this::roleplayersquill$paste),
				new IconButton(0, 0, com.glamardor.roleplayersquill.screen.Icons.CLEAR,
						Component.translatable("roleplayersquill.tool.sign_clear"),
						() -> roleplayersquill$setAll(new String[0])).onlyWhen(written));
		int x = this.width / 2 - 64 - IconButton.SIZE;
		int top = Math.round(getSignYOffset()) - (buttons.size() * (IconButton.SIZE + 1)) / 2;
		for (int i = 0; i < buttons.size(); i++) {
			IconButton button = buttons.get(i);
			button.setPosition(x, top + i * (IconButton.SIZE + 1));
			roleplayersquill$add(button);
		}
	}

	@Unique
	private void roleplayersquill$copy() {
		int last = messages.length;
		while (last > 0 && (messages[last - 1] == null || messages[last - 1].isEmpty())) {
			last--;
		}
		StringBuilder out = new StringBuilder();
		for (int row = 0; row < last; row++) {
			if (row > 0) {
				out.append('\n');
			}
			out.append(messages[row] == null ? "" : messages[row]);
		}
		this.minecraft.keyboardHandler.setClipboard(out.toString());
	}

	@Unique
	private void roleplayersquill$paste() {
		String clipboard = this.minecraft.keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isEmpty()) {
			return;
		}
		roleplayersquill$setAll(clipboard.split("\r?\n|\r", -1));
	}

	/** Writes every row through the screen's own setter, so the sign in the world follows too. */
	@Unique
	private void roleplayersquill$setAll(String[] lines) {
		int width = sign.getMaxTextLineWidth();
		for (int row = 0; row < messages.length; row++) {
			String line = row < lines.length ? net.minecraft.util.StringUtil.filterText(lines[row]) : "";
			this.line = row;
			setMessage(this.font.plainSubstrByWidth(line, width));
		}
		this.line = 0;
		if (signField != null) {
			signField.setCursorToEnd();
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
	@Inject(method = "extractSignText", at = @At("TAIL"))
	private void roleplayersquill$spelling(net.minecraft.client.gui.GuiGraphicsExtractor context, org.joml.Vector2f cursor, CallbackInfo ci) {
		if (messages == null || !com.glamardor.roleplayersquill.screen.SpellMarks.wanted()) {
			return;
		}
		int lineHeight = sign.getTextLineHeight();
		int half = 4 * lineHeight / 2;
		for (int row = 0; row < messages.length; row++) {
			String line = messages[row];
			if (line == null || line.isBlank()) {
				continue;
			}
			int left = -this.font.width(line) / 2;
			int top = row * lineHeight - half;
			com.glamardor.roleplayersquill.screen.SpellMarks.draw(context, line,
					index -> left + this.font.width(line.substring(0, Math.min(index, line.length()))),
					top, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
		}
	}
}
