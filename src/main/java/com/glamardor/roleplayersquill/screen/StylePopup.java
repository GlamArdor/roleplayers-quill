package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.ParagraphStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The list of paragraph looks, hanging under the button that opens it.
 *
 * <p>Each entry is drawn the way it sets a paragraph – the heading bold and centred, the caption
 * italic and to the right – so that the list is its own explanation and nobody has to try one to
 * find out what it does.
 */
public final class StylePopup {
	private static final int WIDTH = 132;
	private static final int ROW = 14;
	private static final int TOP = 16;

	private final Consumer<ParagraphStyle> whenPicked;
	private final Supplier<ParagraphStyle> current;

	private int x;
	private int y;

	public StylePopup(Consumer<ParagraphStyle> whenPicked, Supplier<ParagraphStyle> current) {
		this.whenPicked = whenPicked;
		this.current = current;
	}

	public int height() {
		return TOP + ParagraphStyle.values().length * ROW + 6;
	}

	/** Puts the list under the button that opened it, and keeps it on the screen. */
	public void layout(int anchorX, int anchorY, int screenWidth, int screenHeight) {
		x = Math.max(2, Math.min(anchorX, screenWidth - WIDTH - 2));
		y = Math.max(2, Math.min(anchorY, screenHeight - height() - 2));
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + height();
	}

	private int rowAt(double mouseX, double mouseY) {
		if (mouseX < x || mouseX > x + WIDTH) {
			return -1;
		}
		int row = (int) Math.floor((mouseY - (y + TOP)) / (double) ROW);
		return row >= 0 && row < ParagraphStyle.values().length ? row : -1;
	}

	public boolean pickAt(double mouseX, double mouseY) {
		int row = rowAt(mouseX, mouseY);
		if (row < 0) {
			return false;
		}
		whenPicked.accept(ParagraphStyle.values()[row]);
		return true;
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		context.fill(x, y, x + WIDTH, y + height(), 0xF0181818);
		context.drawBorder(x, y, WIDTH, height(), 0xFF000000);
		context.drawText(textRenderer, Text.translatable("roleplayersquill.tool.paragraph"),
				x + 6, y + 4, 0xFFE8D8A0, false);

		ParagraphStyle now = current.get();
		int hovered = rowAt(mouseX, mouseY);
		ParagraphStyle[] all = ParagraphStyle.values();
		for (int i = 0; i < all.length; i++) {
			ParagraphStyle style = all[i];
			int top = y + TOP + i * ROW;
			if (i == hovered) {
				context.fill(x + 2, top, x + WIDTH - 2, top + ROW, 0x604C7BB0);
			}
			if (style == now) {
				context.fill(x + 3, top + 3, x + 6, top + ROW - 3, 0xFFE8D8A0);
			}
			// Shown in its own look, which is the shortest description there is.
			Text label = style.label().copy().styled(vanilla -> vanilla
					.withBold(style == ParagraphStyle.HEADING || style == ParagraphStyle.SUBHEADING)
					.withItalic(style == ParagraphStyle.QUOTE || style == ParagraphStyle.CAPTION));
			int width = textRenderer.getWidth(label);
			int left = switch (style) {
				case HEADING -> x + (WIDTH - width) / 2;
				case CAPTION -> x + WIDTH - width - 8;
				case QUOTE -> x + 16;
				default -> x + 10;
			};
			context.drawText(textRenderer, label, left, top + 3, 0xFFE0E0E0, false);
		}
	}
}
