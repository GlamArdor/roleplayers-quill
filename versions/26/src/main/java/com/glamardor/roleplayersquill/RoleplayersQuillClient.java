package com.glamardor.roleplayersquill;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.gui.ConfigScreenFactory;
import com.glamardor.roleplayersquill.gui.LivePreview;
import com.glamardor.roleplayersquill.text.Hyphenator;
import com.glamardor.roleplayersquill.text.Widths;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Sets the mod up: two key bindings, a tick for the live settings preview, and a font watch. */
public final class RoleplayersQuillClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(RoleplayersQuill.MOD_ID, "main"));

	public static KeyMapping dictateKey;
	public static KeyMapping settingsKey;

	@Override
	public void onInitializeClient() {
		QuillConfig.get();

		dictateKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.roleplayersquill.dictate", InputConstants.Type.KEYBOARD, InputConstants.KEY_INSERT, CATEGORY));
		settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.roleplayersquill.settings", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LivePreview.tick(client);
			while (settingsKey.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(ConfigScreenFactory.create(null));
				}
			}
		});

		// Every pixel this mod counts comes from the font, and a resource pack is free to change it.
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				Identifier.fromNamespaceAndPath(RoleplayersQuill.MOD_ID, "measurements"),
				(ResourceManagerReloadListener) manager -> {
					Widths.clear();
					Hyphenator.reload();
					// Which characters exist is a property of the font, and the font just changed.
					com.glamardor.roleplayersquill.screen.Symbols.forgetFont();
				});
	}

}
