package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.CodeToolbar;
import com.glamardor.roleplayersquill.screen.IconButton;
import com.glamardor.roleplayersquill.screen.SpellMarks;
import com.glamardor.roleplayersquill.screen.SpellPopup;
import com.glamardor.roleplayersquill.screen.SymbolBar;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * A row of symbols over the chat box.
 *
 * <p>Typing « or ★ or a suit into a roleplay chat means finding it somewhere and pasting it, every
 * time. A row of the ones you use, plus a button that opens the whole browser, is most of what
 * anyone wants from a chat toolbar.
 *
 * <p>Formatting codes are a separate setting and off by default. The chat is the one place a section
 * sign cannot go – the server checks every message for one and disconnects the client that sends it
 * – so the best a code button can do is insert whatever character the server's own colour plugin
 * reads instead, which is a guess about somebody else's plugin.
 *
 * <h2>Getting the click</h2>
 *
 * <p>The buttons are given the click before {@link ChatScreen} sees it. Its own {@code mouseClicked}
 * offers every click to the command suggester and then to the chat log before it ever reaches its
 * children, and both of those sit exactly where this row does – so a button added the ordinary way
 * is drawn, highlights under the cursor, and never fires.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow
	protected TextFieldWidget chatField;

	/**
	 * Assigned in the injection rather than declared with an initialiser: a field initialiser in a
	 * mixin has to be lifted into the target's constructor, and there is no reason to rely on that
	 * when the field is only read after {@code init} has run.
	 */
	@Unique
	private List<IconButton> roleplayersquill$buttons;

	/**
	 * What was typed, carried across a rebuild of the screen.
	 *
	 * <p>{@link ChatScreen#init} makes a new text box and fills it from the text the chat was
	 * <em>opened</em> with, so anything typed since is gone the moment the screen is built again –
	 * which happens both when the symbol browser closes and returns here, and when the row pages
	 * along. Without this, picking a symbol emptied the chat and left nothing to send.
	 */
	@Unique
	private static String roleplayersquill$carried;

	protected ChatScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void roleplayersquill$addBar(CallbackInfo ci) {
		String carried = roleplayersquill$carried;
		roleplayersquill$carried = null;
		if (carried != null && chatField != null) {
			chatField.setText(carried);
		} else {
			// A fresh opening of the chat rather than a rebuild of it: this is the moment the row of
			// symbols may safely reorder itself around what has been used since.
			SymbolBar.reset();
		}

		roleplayersquill$buttons = new ArrayList<>();
		QuillConfig config = QuillConfig.get();
		int y = this.height - 34;

		if (config.chatSymbols) {
			// Half the width. A row all the way across is a wall of symbols over the chat log.
			List<IconButton> symbols = SymbolBar.build((ChatScreen) (Object) this, this.width / 2,
					roleplayersquill$insert(), this::clearAndInit);
			SymbolBar.place(symbols, 3, y);
			roleplayersquill$add(symbols);
			y -= IconButton.SIZE + 1;
		}

		if (config.chatFormatting) {
			char prefix = config.chatCodePrefix.charAt(0);
			List<IconButton> codes = CodeToolbar.build((ChatScreen) (Object) this, prefix,
					roleplayersquill$insert());
			SymbolBar.place(codes, 3, y);
			roleplayersquill$add(codes);
		}
	}

	@Unique
	private java.util.function.Consumer<String> roleplayersquill$insert() {
		return text -> {
			if (chatField == null) {
				return;
			}
			chatField.write(text);
			// Kept aside at once: whatever comes next may rebuild the screen, and the new text box
			// would otherwise be filled from what the chat was opened with.
			roleplayersquill$carried = chatField.getText();
			setFocused(chatField);
		};
	}

	@Unique
	private void roleplayersquill$add(List<IconButton> buttons) {
		for (IconButton button : buttons) {
			addDrawableChild(button);
			roleplayersquill$buttons.add(button);
		}
	}

	/**
	 * Takes the click before the suggester and the chat log are offered it.
	 *
	 * <p>Only when the cursor is actually on one of our buttons, so nothing else about clicking in
	 * the chat changes.
	 */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$click(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> info) {
		// The corrections menu stands over the chat log and answers before anything under it.
		if (roleplayersquill$spell != null) {
			if (!roleplayersquill$spell.contains(mouseX, mouseY)) {
				roleplayersquill$spell = null;
			} else {
				roleplayersquill$spell.pickAt(mouseX, mouseY);
			}
			info.setReturnValue(true);
			return;
		}
		if (button == 1 && roleplayersquill$openSpell(mouseX, mouseY)) {
			info.setReturnValue(true);
			return;
		}
		if (roleplayersquill$buttons == null || button != 0) {
			return;
		}
		for (IconButton candidate : roleplayersquill$buttons) {
			if (candidate.visible && candidate.isMouseOver(mouseX, mouseY)) {
				// Before anything, because the button may open the browser or page the row along,
				// and both of those build this screen again from scratch.
				if (chatField != null) {
					roleplayersquill$carried = chatField.getText();
				}
				candidate.mouseClicked(mouseX, mouseY, button);
				info.setReturnValue(true);
				return;
			}
		}
	}

	/**
	 * The spelling of what is being typed, marked in the chat box itself.
	 *
	 * <p>The same dictionary and the same menu as a book, because it is the same writing: on a
	 * roleplay server the chat is where most of it happens, and it is the place where a line is typed
	 * fastest and read over least.
	 *
	 * <p>Where each character sits is asked of the box rather than worked out. It scrolls what it
	 * holds, so the first character drawn is not the first character typed, and
	 * {@code getCharacterX} is the box's own answer to exactly that.
	 */
	@Inject(method = "render", at = @At("TAIL"))
	private void roleplayersquill$spelling(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (chatField == null || !SpellMarks.wanted()) {
			return;
		}
		SpellMarks.draw(context, chatField.getText(), roleplayersquill$xOf(),
				roleplayersquill$textY(), chatField.getX(), chatField.getX() + chatField.getWidth());
		if (roleplayersquill$spell != null) {
			roleplayersquill$spell.render(context, mouseX, mouseY);
		}
	}

	/**
	 * Where a character of the chat box sits across the screen.
	 *
	 * <p>Counted from the first character the box is showing rather than from the first it holds: a
	 * long message scrolls, and the box's own answer to this question is the one its cursor needs,
	 * which pretends nothing has scrolled.
	 */
	@Unique
	private java.util.function.IntUnaryOperator roleplayersquill$xOf() {
		String text = chatField.getText();
		int first = ((TextFieldWidgetAccessor) chatField).roleplayersquill$firstCharacterIndex();
		int left = chatField.getX() + (chatField.drawsBackground() ? 4 : 0);
		return index -> {
			int at = Math.max(Math.min(index, text.length()), first);
			return left + this.textRenderer.getWidth(text.substring(Math.min(first, text.length()), at));
		};
	}

	/** Where the box draws its text, which is not where the box is when it has a background. */
	@Unique
	private int roleplayersquill$textY() {
		return chatField.drawsBackground()
				? chatField.getY() + (chatField.getHeight() - 8) / 2
				: chatField.getY();
	}

	@Unique
	private SpellPopup roleplayersquill$spell;

	/**
	 * Opens the corrections for the word under the cursor, on the right button.
	 *
	 * <p>Above the box rather than below it: below the box is the bottom of the screen.
	 */
	@Unique
	private boolean roleplayersquill$openSpell(double mouseX, double mouseY) {
		if (chatField == null || !SpellMarks.wanted()
				|| mouseY < chatField.getY() - 2 || mouseY > chatField.getY() + chatField.getHeight()) {
			return false;
		}
		String text = chatField.getText();
		com.glamardor.roleplayersquill.text.Spelling.Word word =
				SpellMarks.at(text, roleplayersquill$xOf(), mouseX);
		if (word == null) {
			return false;
		}
		roleplayersquill$spell = new SpellPopup(word.text(),
				com.glamardor.roleplayersquill.text.Spelling.suggest(word.text()),
				replacement -> {
					String mended = text.substring(0, word.from()) + replacement + text.substring(word.to());
					chatField.setText(mended);
					chatField.setCursor(word.from() + replacement.length(), false);
					roleplayersquill$carried = mended;
					roleplayersquill$spell = null;
				},
				() -> {
					com.glamardor.roleplayersquill.text.Spelling.learn(word.text());
					roleplayersquill$spell = null;
				},
				() -> {
					com.glamardor.roleplayersquill.text.Spelling.ignore(word.text());
					roleplayersquill$spell = null;
				});
		roleplayersquill$spell.layout((int) mouseX, chatField.getY() - 2, this.width,
				chatField.getY() - 2, this.textRenderer);
		return true;
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void roleplayersquill$preview(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		QuillConfig config = QuillConfig.get();
		if (!config.chatFormatting || chatField == null) {
			return;
		}
		char prefix = config.chatCodePrefix.charAt(0);
		String typed = chatField.getText();
		int y = this.height - 34 - (config.chatSymbols ? IconButton.SIZE + 1 : 0) - 12;
		if (typed.indexOf(prefix) < 0) {
			context.drawText(this.textRenderer, CodeToolbar.chatNote(prefix), 4, y, 0xFFFFFFFF, false);
			return;
		}
		context.fill(2, y - 2, this.width - 2, y + 10, 0x90000000);
		context.drawText(this.textRenderer, CodeToolbar.preview(typed, prefix), 4, y, 0xFFFFFFFF, false);
	}
}
