package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.QuillStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The little bar of formatting buttons for everywhere that is not the book editor: signs, the
 * anvil, the chat.
 *
 * <p>There is no document to style in any of those – there is a string, and the way a string says
 * "bold" is by carrying a code. So these buttons write codes, and the only question is which
 * character starts them. On a sign it is the section sign itself, which the server passes through
 * untouched. In the chat it cannot be: the server checks every message for one and disconnects the
 * client that sends it, so what goes in is whatever the server's own plugin reads instead, an
 * ampersand by default.
 */
public final class CodeToolbar {
	private CodeToolbar() {
	}

	/** Builds the row. The caller positions it and adds the buttons to its own screen. */
	public static List<IconButton> build(Screen owner, char prefix, Consumer<String> insert) {
		List<IconButton> buttons = new ArrayList<>();
		buttons.add(code(Icons.BOLD, "bold", prefix, 'l', insert));
		buttons.add(code(Icons.ITALIC, "italic", prefix, 'o', insert));
		buttons.add(code(Icons.UNDERLINE, "underline", prefix, 'n', insert));
		buttons.add(code(Icons.STRIKE, "strike", prefix, 'm', insert));
		buttons.add(code(Icons.OBFUSCATED, "obfuscated", prefix, 'k', insert));
		buttons.add(code(Icons.CLEAR, "reset", prefix, 'r', insert));

		buttons.add(new IconButton(0, 0, Icons.COLOR_SWATCH,
				Text.translatable("roleplayersquill.tool.colour"),
				() -> MinecraftClient.getInstance().setScreen(new ColourScreen(owner, colour -> {
					if (colour == QuillStyle.INHERIT) {
						insert.accept(prefix + "r");
						return;
					}
					int index = QuillStyle.PLAIN.withColor(colour).legacyColorIndex();
					insert.accept("" + prefix + "0123456789abcdef".charAt(Math.max(0, index)));
				}))));

		buttons.add(new IconButton(0, 0, Icons.SYMBOL,
				Text.translatable("roleplayersquill.tool.symbols"),
				() -> MinecraftClient.getInstance().setScreen(new SymbolScreen(owner, insert::accept))));

		return buttons;
	}

	private static IconButton code(Icons.Icon icon, String key, char prefix, char letter, Consumer<String> insert) {
		return new IconButton(0, 0, icon, Text.translatable("roleplayersquill.tool." + key),
				() -> insert.accept("" + prefix + letter));
	}

	/** Lays a row of buttons out centred on a point. */
	public static void place(List<IconButton> buttons, int centreX, int y) {
		int step = IconButton.SIZE + 1;
		int x = centreX - buttons.size() * step / 2;
		for (IconButton button : buttons) {
			button.setX(x);
			button.setY(y);
			x += step;
		}
	}

	/**
	 * What a line with codes in it will look like, for a preview.
	 *
	 * <p>The chat never sees a section sign, so a preview has to put one back to show what the
	 * server's plugin will make of the ampersands.
	 */
	public static Text preview(String line, char prefix) {
		if (prefix == '§') {
			return Text.literal(line);
		}
		StringBuilder out = new StringBuilder(line.length());
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == prefix && i + 1 < line.length()
					&& "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(line.charAt(i + 1)) >= 0) {
				out.append('§').append(line.charAt(i + 1));
				i++;
				continue;
			}
			out.append(c);
		}
		return Text.literal(out.toString());
	}

	/** A short line of guidance, so nobody has to find out by being disconnected. */
	public static MutableText chatNote(char prefix) {
		return Text.translatable("roleplayersquill.chat.note", String.valueOf(prefix))
				.formatted(Formatting.DARK_GRAY);
	}
}
