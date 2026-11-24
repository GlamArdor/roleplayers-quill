package com.glamardor.roleplayersquill.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The providers a font set draws from, in the order it asks them. See {@link FontManagerAccessor}. */
@Mixin(FontSet.class)
public interface FontSetAccessor {
	@Accessor("activeProviders")
	List<GlyphProvider> roleplayersquill$activeProviders();
}
