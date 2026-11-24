package com.glamardor.roleplayersquill.mixin;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches the font itself, to ask whether it has a particular character.
 *
 * <p>The symbol browser offers several hundred characters, and which of them the game can actually
 * draw depends on the font that is loaded – a resource pack can add some and take others away. A
 * character the font does not have is drawn as a hollow box, and a shelf full of hollow boxes is
 * worse than a shorter shelf. There is no public way to ask, so this opens the doors needed:
 * the font set by name here, its providers in {@link FontSetAccessor}, the manager in
 * {@link MinecraftAccessor}.
 */
@Mixin(FontManager.class)
public interface FontManagerAccessor {
	@Invoker("getFontSetRaw")
	FontSet roleplayersquill$getFontSet(Identifier id);
}
