package com.glamardor.roleplayersquill.gui;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

/** Hands out whichever settings screen the player can run: Cloth Config's when it is there, ours otherwise. */
public final class ConfigScreenFactory {
	private ConfigScreenFactory() {
	}

	public static boolean isClothPresent() {
		FabricLoader loader = FabricLoader.getInstance();
		return loader.isModLoaded("cloth-config") || loader.isModLoaded("cloth-config2");
	}

	public static Screen create(@Nullable Screen parent) {
		if (isClothPresent()) {
			try {
				return ClothConfigScreens.build(parent);
			} catch (Throwable error) {
				// A Cloth major version bump should fall back to our own screen, not crash the game.
				RoleplayersQuill.LOGGER.warn("The Cloth Config screen failed; using the built-in one", error);
			}
		}
		return new FallbackConfigScreen(parent);
	}
}
