package com.glamardor.roleplayersquill.text;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pieces of a document rather than a whole one: a dateline, a signature, a heading block.
 *
 * <p>A template is a page and replaces what you are looking at. These go into the page being
 * written, where the caret is, and are the things actually typed over and over: the date at the top
 * of a decree and the name at the bottom of it.
 */
public enum TextSet {
	/** Today, in the reckoning the server keeps, right-aligned the way a dateline is written. */
	DATE(2),
	/** A rule, "signed", and the name the server shows for whoever is holding the quill. */
	SIGNATURE(3);

	private final int lines;

	TextSet(int lines) {
		this.lines = lines;
	}

	public Text label() {
		return Text.translatable(key() + ".name");
	}

	private String key() {
		return "roleplayersquill.set." + name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The name this player writes under.
	 *
	 * <p>The one on the tab list, not the one on the account. A roleplay server renames everybody
	 * there and nowhere else, so a signature taken from the account name signs somebody else's
	 * documents – the player's, not the character's. Falls back to the account name only when there
	 * is no list to ask, which is to say in single player.
	 */
	public static String writerName() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return "";
		}
		if (client.getNetworkHandler() != null) {
			PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
			if (entry != null && entry.getDisplayName() != null) {
				String shown = clean(entry.getDisplayName().getString());
				if (!shown.isEmpty()) {
					return shown;
				}
			}
		}
		return client.player.getGameProfile().getName();
	}

	/**
	 * The name out of what the tab list shows, and nothing else.
	 *
	 * <p>What stands there is decorated: colour codes, and square brackets in front holding a rank,
	 * a guild or an id. None of that is the character's name, and a signature carrying it is a
	 * signature with somebody's permissions written into it. The colours come off because a book
	 * written on a page has colours of its own, and a name that arrives already red would ignore
	 * them.
	 */
	public static String clean(String shown) {
		String name = LegacyCodec.strip(shown).strip();
		while (name.startsWith("[")) {
			int close = name.indexOf(']');
			if (close < 0) {
				break;
			}
			name = name.substring(close + 1).strip();
		}
		return name;
	}

	/** The set, as paragraphs ready to go into a page. */
	public List<Paragraph> paragraphs() {
		List<Paragraph> out = new ArrayList<>();
		for (int i = 1; i <= lines; i++) {
			String line = Text.translatable(key() + ".line" + i).getString()
					.replace("{date_of}", ServerDate.todayAfterPreposition())
					.replace("{date}", ServerDate.today())
					.replace("{name}", writerName());

			Alignment alignment = Alignment.LEFT;
			ParagraphStyle style = ParagraphStyle.NORMAL;
			// Two markers may stand in front of a line, in either order: how it is set out and how
			// it is aligned. Both live in the language file so a translation can move them.
			while (true) {
				int bar = line.indexOf('|');
				if (bar <= 0) {
					break;
				}
				String head = line.substring(0, bar).toUpperCase(Locale.ROOT);
				try {
					alignment = Alignment.valueOf(head);
				} catch (IllegalArgumentException notAlignment) {
					try {
						style = ParagraphStyle.valueOf(head);
					} catch (IllegalArgumentException notStyle) {
						break;
					}
				}
				line = line.substring(bar + 1);
			}

			Paragraph paragraph = line.isEmpty() ? new Paragraph() : new Paragraph(line, QuillStyle.PLAIN);
			style.applyTo(paragraph);
			paragraph.setAlignment(alignment);
			out.add(paragraph);
		}
		return out;
	}
}
