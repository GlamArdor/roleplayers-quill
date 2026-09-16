package com.glamardor.roleplayersquill;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.gui.ConfigScreenFactory;
import com.glamardor.roleplayersquill.gui.LivePreview;
import com.glamardor.roleplayersquill.text.Hyphenator;
import com.glamardor.roleplayersquill.text.Widths;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Sets the mod up: two key bindings, a tick for the live settings preview, and a font watch. */
public final class RoleplayersQuillClient implements ClientModInitializer {
	private static final String CATEGORY = "key.categories.roleplayersquill";

	public static KeyBinding dictateKey;
	public static KeyBinding settingsKey;

	@Override
	public void onInitializeClient() {
		QuillConfig.get();

		dictateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.roleplayersquill.dictate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, CATEGORY));
		settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.roleplayersquill.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LivePreview.tick(client);
			while (settingsKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(ConfigScreenFactory.create(null));
				}
			}
		});

		// Every pixel this mod counts comes from the font, and a resource pack is free to change it.
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return Identifier.of(RoleplayersQuill.MOD_ID, "measurements");
					}

					@Override
					public void reload(ResourceManager manager) {
						Widths.clear();
						Hyphenator.reload();
						// Which characters exist is a property of the font, and the font just changed.
						com.glamardor.roleplayersquill.screen.Symbols.forgetFont();
					}
				});
	}

}
