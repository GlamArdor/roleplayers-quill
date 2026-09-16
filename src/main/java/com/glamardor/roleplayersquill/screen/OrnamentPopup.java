package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.FrameStyle;
import com.glamardor.roleplayersquill.text.Ornaments;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Dividers to drop into a page, and the frame, hanging under the button that opens them.
 *
 * <p>One row per divider, drawn small but drawn – a list of names would say nothing about what a
 * flourish looks like, and looking is the only reason to pick one over another.
 */
public final class OrnamentPopup {
	private static final int WIDTH = 148;
	private static final int ROW = 14;
	private static final int TOP = 16;

	private final Consumer<Ornaments.Divider> whenPicked;
	private final Consumer<FrameStyle> whenFramed;

	private int x;
	private int y;

	public OrnamentPopup(Consumer<Ornaments.Divider> whenPicked, Consumer<FrameStyle> whenFramed) {
		this.whenPicked = whenPicked;
		this.whenFramed = whenFramed;
	}

	/** One row for every divider, then one for every frame, then one for taking the frame off. */
	private static int rows() {
		return Ornaments.Divider.values().length + FrameStyle.values().length;
	}

	public int height() {
		return TOP + rows() * ROW + 6;
	}

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
		return row >= 0 && row < rows() ? row : -1;
	}

	public boolean pickAt(double mouseX, double mouseY) {
		int row = rowAt(mouseX, mouseY);
		if (row < 0) {
			return false;
		}
		Ornaments.Divider[] all = Ornaments.Divider.values();
		if (row < all.length) {
			whenPicked.accept(all[row]);
		} else {
			// The frames come after the dividers, with "no frame" first among them.
			whenFramed.accept(FrameStyle.values()[row - all.length]);
		}
		return true;
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		context.fill(x, y, x + WIDTH, y + height(), 0xF0181818);
		context.drawBorder(x, y, WIDTH, height(), 0xFF000000);
		context.drawText(textRenderer, Text.translatable("roleplayersquill.tool.ornament"),
				x + 6, y + 4, 0xFFE8D8A0, false);

		int hovered = rowAt(mouseX, mouseY);
		Ornaments.Divider[] all = Ornaments.Divider.values();
		for (int i = 0; i < rows(); i++) {
			int top = y + TOP + i * ROW;
			if (i == hovered) {
				context.fill(x + 2, top, x + WIDTH - 2, top + ROW, 0x604C7BB0);
			}
			Text label = i < all.length ? all[i].label() : FrameStyle.values()[i - all.length].label();
			if (i == all.length) {
				// A hairline between the dividers and the frames: they are different kinds of thing.
				context.fill(x + 6, top - 1, x + WIDTH - 6, top, 0xFF4A4A4A);
			}
			context.drawText(textRenderer, label, x + 8, top + 3, 0xFFE0E0E0, false);
		}
	}
}
