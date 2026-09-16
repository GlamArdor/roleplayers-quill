package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.config.QuillConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The list of correcting rules, hanging under the button that turns them on.
 *
 * <p>The same rules are in the settings screen, and that is where they belong for setting up once.
 * This is for the other case: something has just been corrected that should not have been, and the
 * rule that did it needs turning off now, with the book open and the sentence on screen. Walking out
 * to the mod settings and back for that is enough of a walk that people stop using the feature
 * instead.
 */
public final class CorrectPopup {
	private static final int WIDTH = 168;
	private static final int ROW = 14;
	private static final int TOP = 16;

	/** One switch: what it is called, how to read it, how to set it. */
	private record Rule(String key, BooleanSupplier get, Consumer<Boolean> set) {
	}

	private final List<Rule> rules;
	private final Runnable whenChanged;

	private int x;
	private int y;

	public CorrectPopup(Runnable whenChanged) {
		this.whenChanged = whenChanged;
		QuillConfig config = QuillConfig.get();
		this.rules = List.of(
				new Rule("auto_quotes", () -> config.autoQuotes, v -> config.autoQuotes = v),
				new Rule("auto_dashes", () -> config.autoDashes, v -> config.autoDashes = v),
				new Rule("auto_ellipsis", () -> config.autoEllipsis, v -> config.autoEllipsis = v),
				new Rule("auto_apostrophe", () -> config.autoApostrophe, v -> config.autoApostrophe = v),
				new Rule("auto_signs", () -> config.autoSigns, v -> config.autoSigns = v),
				new Rule("auto_capitals", () -> config.autoCapitals, v -> config.autoCapitals = v));
	}

	public static int width() {
		return WIDTH;
	}

	public int height() {
		return TOP + rules.size() * ROW + 6;
	}

	/** Puts the list under the button that opened it, and keeps it on the screen. */
	public void layout(int anchorX, int anchorY, int screenWidth, int screenHeight,
			Consumer<ClickableWidget> adder, TextRenderer textRenderer) {
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
		// Math.floor rather than a cast, which rounds towards zero and makes the row above the list
		// into row nought.
		int row = (int) Math.floor((mouseY - (y + TOP)) / (double) ROW);
		return row >= 0 && row < rules.size() ? row : -1;
	}

	/** Flips the rule that was clicked. Returns whether the click was one. */
	public boolean pickAt(double mouseX, double mouseY) {
		int row = rowAt(mouseX, mouseY);
		if (row < 0) {
			return false;
		}
		Rule rule = rules.get(row);
		rule.set().accept(!rule.get().getAsBoolean());
		QuillConfig.get().save();
		whenChanged.run();
		return true;
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		TextRenderer textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
		context.fill(x, y, x + WIDTH, y + height(), 0xF0181818);
		context.drawBorder(x, y, WIDTH, height(), 0xFF000000);
		context.drawText(textRenderer, Text.translatable("roleplayersquill.option.auto_correct"),
				x + 6, y + 4, 0xFFE8D8A0, false);

		boolean on = QuillConfig.get().autoCorrect;
		int hovered = rowAt(mouseX, mouseY);
		for (int i = 0; i < rules.size(); i++) {
			Rule rule = rules.get(i);
			int top = y + TOP + i * ROW;
			if (i == hovered) {
				context.fill(x + 2, top, x + WIDTH - 2, top + ROW, 0x604C7BB0);
			}
			boolean ticked = rule.get().getAsBoolean();
			context.drawBorder(x + 6, top + 2, 9, 9, 0xFF909090);
			if (ticked) {
				context.fill(x + 8, top + 4, x + 13, top + 9, on ? 0xFF6AC06A : 0xFF606060);
			}
			Text label = Text.translatable("roleplayersquill.option." + rule.key());
			// Greyed out while the whole thing is off, because a tick that does nothing is a lie.
			context.drawText(textRenderer, on ? label : label.copy().formatted(Formatting.DARK_GRAY),
					x + 20, top + 3, 0xFFE0E0E0, false);
		}
	}
}
