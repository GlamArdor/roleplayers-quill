package com.glamardor.roleplayersquill.mixin;

import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches the font itself, to ask whether it has a particular character.
 *
 * <p>The symbol browser offers several hundred characters, and which of them the game can actually
 * draw depends on the font that is loaded – a resource pack can add some and take others away. A
 * character the font does not have is drawn as a hollow box, and a shelf full of hollow boxes is
 * worse than a shorter shelf. There is no public way to ask, so this opens the one door needed.
 */
@Mixin(TextRenderer.class)
public interface TextRendererAccessor {
	@Invoker("getFontStorage")
	FontStorage roleplayersquill$getFontStorage(Identifier id);
}
