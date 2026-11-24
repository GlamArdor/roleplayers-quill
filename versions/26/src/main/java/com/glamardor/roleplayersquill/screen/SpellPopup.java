package com.glamardor.roleplayersquill.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * What to do about a word that is not a word, hanging under the word itself.
 *
 * <p>The menu every word processor opens on a right click, with the same three answers: put
 * something else there, keep the word for good, or leave it alone this once. The last two are what
 * makes the whole thing usable in a book full of invented names – a name is added once and never
 * asked about again.
 */
public final class SpellPopup {
	private static final int ROW = 12;
	private static final int TOP = 14;

	private final String word;
	private final List<String> suggestions;
	private final Consumer<String> replace;
	private final Runnable learn;
	private final Runnable ignore;

	private int x;
	private int y;
	private int width = 120;

	public SpellPopup(String word, List<String> suggestions, Consumer<String> replace,
			Runnable learn, Runnable ignore) {
		this.word = word;
		this.suggestions = List.copyOf(suggestions);
		this.replace = replace;
		this.learn = learn;
		this.ignore = ignore;
	}

	/** The rows, in the order they are drawn: the suggestions, then the two standing answers. */
	private List<Component> rows() {
		List<Component> rows = new ArrayList<>();
		if (suggestions.isEmpty()) {
			rows.add(Component.translatable("roleplayersquill.spell.nothing").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			for (String suggestion : suggestions) {
				rows.add(Component.literal(suggestion));
			}
		}
		rows.add(Component.translatable("roleplayersquill.spell.learn").withStyle(ChatFormatting.GOLD));
		rows.add(Component.translatable("roleplayersquill.spell.ignore").withStyle(ChatFormatting.GRAY));
		return rows;
	}

	public int height() {
		return TOP + rows().size() * ROW + 4;
	}

	/**
	 * Puts the menu under the word, or over it when there is not room underneath.
	 *
	 * <p>"Room" is not the bottom of the screen: the row with Sign and Done is down there, and a menu
	 * that reaches it is a menu whose last two entries – the two that matter – are behind a button.
	 * So the floor is handed in, and the menu goes above the word rather than through it.
	 */
	public void layout(int anchorX, int anchorY, int screenWidth, int floor, Font textRenderer) {
		int widest = textRenderer.width(word) + 20;
		for (Component row : rows()) {
			widest = Math.max(widest, textRenderer.width(row) + 16);
		}
		width = Math.max(110, Math.min(widest, 200));
		x = Math.max(2, Math.min(anchorX, screenWidth - width - 2));
		int height = height();
		y = anchorY + height <= floor ? anchorY : Math.max(2, anchorY - height - 12);
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height();
	}

	private int rowAt(double mouseX, double mouseY) {
		if (mouseX < x || mouseX > x + width) {
			return -1;
		}
		int row = (int) Math.floor((mouseY - (y + TOP)) / (double) ROW);
		return row >= 0 && row < rows().size() ? row : -1;
	}

	/** Answers the click, and says whether it was one of ours. */
	public boolean pickAt(double mouseX, double mouseY) {
		int row = rowAt(mouseX, mouseY);
		if (row < 0) {
			return false;
		}
		int offered = suggestions.isEmpty() ? 1 : suggestions.size();
		if (row < offered) {
			if (!suggestions.isEmpty()) {
				replace.accept(suggestions.get(row));
			}
			return true;
		}
		if (row == offered) {
			learn.run();
		} else {
			ignore.run();
		}
		return true;
	}

	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		Font textRenderer = Minecraft.getInstance().font;
		int height = height();
		context.fill(x, y, x + width, y + height, 0xF0181818);
		context.outline(x, y, width, height, 0xFF000000);
		context.text(textRenderer, Component.literal(word).withStyle(ChatFormatting.WHITE),
				x + 6, y + 3, 0xFFE8D8A0, false);

		List<Component> rows = rows();
		int hovered = rowAt(mouseX, mouseY);
		boolean nothing = suggestions.isEmpty();
		for (int i = 0; i < rows.size(); i++) {
			int top = y + TOP + i * ROW;
			boolean pickable = !(nothing && i == 0);
			if (i == hovered && pickable) {
				context.fill(x + 2, top, x + width - 2, top + ROW, 0x604C7BB0);
			}
			context.text(textRenderer, rows.get(i), x + 8, top + 2, 0xFFE0E0E0, false);
		}
	}
}
