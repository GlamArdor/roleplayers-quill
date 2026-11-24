package com.glamardor.roleplayersquill.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The font manager, which the game keeps to itself. See {@link FontManagerAccessor}. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("fontManager")
	FontManager roleplayersquill$fontManager();
}
