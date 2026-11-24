package com.glamardor.roleplayersquill.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Puts the settings behind the button Mod Menu draws next to the mod. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return com.glamardor.roleplayersquill.gui.ConfigScreenFactory::create;
	}
}
