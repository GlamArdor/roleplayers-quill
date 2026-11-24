package com.glamardor.roleplayersquill.mixin;

import net.minecraft.client.input.MouseButtonEvent;
import com.glamardor.roleplayersquill.screen.Input;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.CodeToolbar;
import com.glamardor.roleplayersquill.screen.IconButton;
import com.glamardor.roleplayersquill.screen.SpellMarks;
import com.glamardor.roleplayersquill.screen.SpellPopup;
import com.glamardor.roleplayersquill.screen.SymbolBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
	protected EditBox input;

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

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void roleplayersquill$addBar(CallbackInfo ci) {
		String carried = roleplayersquill$carried;
		roleplayersquill$carried = null;
		if (carried != null && input != null) {
			input.setValue(carried);
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
					roleplayersquill$insert(), this::rebuildWidgets);
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
			if (input == null) {
				return;
			}
			input.insertText(text);
			// Kept aside at once: whatever comes next may rebuild the screen, and the new text box
			// would otherwise be filled from what the chat was opened with.
			roleplayersquill$carried = input.getValue();
			setFocused(input);
		};
	}

	@Unique
	private void roleplayersquill$add(List<IconButton> buttons) {
		for (IconButton button : buttons) {
			addRenderableWidget(button);
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
	private void roleplayersquill$click(MouseButtonEvent event, boolean doubleClick,
			CallbackInfoReturnable<Boolean> info) {
		double mouseX = event.x(), mouseY = event.y();
		int button = Input.button(event);
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
				if (input != null) {
					roleplayersquill$carried = input.getValue();
				}
				candidate.mouseClicked(event, doubleClick);
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
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void roleplayersquill$spelling(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (input == null || !SpellMarks.wanted()) {
			return;
		}
		SpellMarks.draw(context, input.getValue(), roleplayersquill$xOf(),
				roleplayersquill$textY(), input.getX(), input.getX() + input.getWidth());
		if (roleplayersquill$spell != null) {
			roleplayersquill$spell.extractRenderState(context, mouseX, mouseY);
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
		String text = input.getValue();
		int first = ((TextFieldWidgetAccessor) input).roleplayersquill$firstCharacterIndex();
		int left = input.getX() + (input.isBordered() ? 4 : 0);
		return index -> {
			int at = Math.max(Math.min(index, text.length()), first);
			return left + this.font.width(text.substring(Math.min(first, text.length()), at));
		};
	}

	/** Where the box draws its text, which is not where the box is when it has a background. */
	@Unique
	private int roleplayersquill$textY() {
		return input.isBordered()
				? input.getY() + (input.getHeight() - 8) / 2
				: input.getY();
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
		if (input == null || !SpellMarks.wanted()
				|| mouseY < input.getY() - 2 || mouseY > input.getY() + input.getHeight()) {
			return false;
		}
		String text = input.getValue();
		com.glamardor.roleplayersquill.text.Spelling.Word word =
				SpellMarks.at(text, roleplayersquill$xOf(), mouseX);
		if (word == null) {
			return false;
		}
		roleplayersquill$spell = new SpellPopup(word.text(),
				com.glamardor.roleplayersquill.text.Spelling.suggest(word.text()),
				replacement -> {
					String mended = text.substring(0, word.from()) + replacement + text.substring(word.to());
					input.setValue(mended);
					input.moveCursorTo(word.from() + replacement.length(), false);
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
		roleplayersquill$spell.layout((int) mouseX, input.getY() - 2, this.width,
				input.getY() - 2, this.font);
		return true;
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void roleplayersquill$preview(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		QuillConfig config = QuillConfig.get();
		if (!config.chatFormatting || input == null) {
			return;
		}
		char prefix = config.chatCodePrefix.charAt(0);
		String typed = input.getValue();
		int y = this.height - 34 - (config.chatSymbols ? IconButton.SIZE + 1 : 0) - 12;
		if (typed.indexOf(prefix) < 0) {
			context.text(this.font, CodeToolbar.chatNote(prefix), 4, y, 0xFFFFFFFF, false);
			return;
		}
		context.fill(2, y - 2, this.width - 2, y + 10, 0x90000000);
		context.text(this.font, CodeToolbar.preview(typed, prefix), 4, y, 0xFFFFFFFF, false);
	}
}
