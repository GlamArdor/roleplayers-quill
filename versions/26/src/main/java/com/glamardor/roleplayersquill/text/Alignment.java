package com.glamardor.roleplayersquill.text;

import net.minecraft.network.chat.Component;

/** Where a line sits inside the 114 pixels a book page gives it. */
public enum Alignment {
	LEFT("left"),
	CENTER("center"),
	RIGHT("right"),
	JUSTIFY("justify");

	private final String id;

	Alignment(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public Component label() {
		return Component.translatable("roleplayersquill.align." + id);
	}

	public Alignment next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static Alignment byId(String id) {
		for (Alignment value : values()) {
			if (value.id.equals(id)) {
				return value;
			}
		}
		return LEFT;
	}
}
