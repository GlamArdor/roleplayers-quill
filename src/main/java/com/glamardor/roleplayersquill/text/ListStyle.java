package com.glamardor.roleplayersquill.text;

import net.minecraft.text.Text;

/**
 * The marker a paragraph carries in front of it.
 *
 * <p>Kept out of the paragraph's own text, so that turning a list off again does not leave the
 * bullets behind for the player to delete by hand, and so that numbering renumbers itself when a
 * line is inserted in the middle.
 */
public enum ListStyle {
	NONE("none"),
	BULLET("bullet"),
	DASH("dash"),
	NUMBER("number"),
	/** a) b) c) – for the second level of a numbered list. */
	LETTER("letter");

	private final String id;

	ListStyle(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public Text label() {
		return Text.translatable("roleplayersquill.list." + id);
	}

	public boolean numbered() {
		return this == NUMBER || this == LETTER;
	}

	/**
	 * The text drawn in front of a paragraph.
	 *
	 * @param ordinal position within the run of list items this paragraph belongs to, counting from 1
	 */
	public String marker(int ordinal) {
		return switch (this) {
			case NONE -> "";
			case BULLET -> "• ";
			case DASH -> "– ";
			case NUMBER -> ordinal + ". ";
			case LETTER -> letter(ordinal) + ") ";
		};
	}

	private static String letter(int ordinal) {
		StringBuilder out = new StringBuilder();
		int n = Math.max(1, ordinal);
		while (n > 0) {
			n--;
			out.insert(0, (char) ('a' + n % 26));
			n /= 26;
		}
		return out.toString();
	}

	public static ListStyle byId(String id) {
		for (ListStyle value : values()) {
			if (value.id.equals(id)) {
				return value;
			}
		}
		return NONE;
	}
}
