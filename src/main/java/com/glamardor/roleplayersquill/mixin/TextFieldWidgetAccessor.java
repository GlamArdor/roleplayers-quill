package com.glamardor.roleplayersquill.mixin;

import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Asks a text box which character it is showing first.
 *
 * <p>A box narrower than what is typed into it scrolls, and from then on the text on the screen
 * begins somewhere in the middle of the text it holds. Everything drawn under that text – the marks
 * under a misspelled word, here – has to start counting from the same place, and the box's own
 * {@code getCharacterX} does not: it answers as though nothing had scrolled, which is right for the
 * cursor it was written for and wrong by a word and a half for anything else.
 */
@Mixin(TextFieldWidget.class)
public interface TextFieldWidgetAccessor {
	@Accessor("firstCharacterIndex")
	int roleplayersquill$firstCharacterIndex();
}
