package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookSender;
import com.glamardor.roleplayersquill.text.QuillStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * The colour picker.
 *
 * <p>Sixteen swatches and a box for anything else. The sixteen are the colours a {@code §} code can
 * name, which is to say the ones every reader will see; a colour typed into the box is kept exactly
 * as typed and sent as a text component if the book can be written that way, and rounded to the
 * nearest of the sixteen if it cannot. The swatch that a typed colour would fall back to is marked,
 * so the compromise is visible before it is made rather than after.
 */
public class ColourScreen extends DialogScreen {
	private static final int SWATCH = 24;

	private final IntConsumer whenPicked;
	private TextFieldWidget hexField;
	private int typed = -1;

	public ColourScreen(@Nullable Screen parent, IntConsumer whenPicked) {
		super(parent, Text.translatable("roleplayersquill.colour.title"));
		this.whenPicked = whenPicked;
		this.panelWidth = 8 * SWATCH + 24;
		this.panelHeight = 2 * SWATCH + 104;
	}

	@Override
	protected void init() {
		super.init();

		hexField = new TextFieldWidget(textRenderer, panelX + 12, panelY + 2 * SWATCH + 44,
				panelWidth - 24, 18, Text.translatable("roleplayersquill.colour.hex"));
		hexField.setMaxLength(7);
		hexField.setPlaceholder(Text.literal("#7B4FA0").formatted(Formatting.DARK_GRAY));
		hexField.setChangedListener(value -> typed = parse(value));
		addDrawableChild(hexField);

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.colour.default"),
						button -> pick(QuillStyle.INHERIT))
				.dimensions(panelX + 12, panelY + panelHeight - 50, panelWidth / 2 - 16, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.colour.apply"),
						button -> {
							if (typed >= 0) {
								pick(typed);
							}
						})
				.dimensions(panelX + panelWidth / 2 + 4, panelY + panelHeight - 50, panelWidth / 2 - 16, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
				.dimensions(panelX + panelWidth / 2 - 50, panelY + panelHeight - 26, 100, 20).build());
	}

	private void pick(int colour) {
		whenPicked.accept(colour);
		close();
	}

	private static int parse(String value) {
		String cleaned = value.trim();
		if (cleaned.startsWith("#")) {
			cleaned = cleaned.substring(1);
		}
		if (cleaned.length() != 6) {
			return -1;
		}
		try {
			return Integer.parseInt(cleaned, 16);
		} catch (NumberFormatException error) {
			return -1;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int index = swatchAt(mouseX, mouseY);
		if (index >= 0) {
			Integer value = QuillStyle.LEGACY_COLORS[index].getColorValue();
			pick(value == null ? QuillStyle.INHERIT : value);
			return true;
		}
		return false;
	}

	private int swatchAt(double mouseX, double mouseY) {
		int gridX = panelX + 12;
		int gridY = panelY + 22;
		for (int i = 0; i < 16; i++) {
			int x = gridX + (i % 8) * SWATCH;
			int y = gridY + (i / 8) * SWATCH;
			if (mouseX >= x && mouseX < x + SWATCH - 2 && mouseY >= y && mouseY < y + SWATCH - 2) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int gridX = panelX + 12;
		int gridY = panelY + 22;
		int nearest = typed >= 0 ? QuillStyle.PLAIN.withColor(typed).legacyColorIndex() : -1;
		for (int i = 0; i < 16; i++) {
			int x = gridX + (i % 8) * SWATCH;
			int y = gridY + (i / 8) * SWATCH;
			Integer value = QuillStyle.LEGACY_COLORS[i].getColorValue();
			context.fill(x, y, x + SWATCH - 2, y + SWATCH - 2, 0xFF000000 | (value == null ? 0 : value));
			boolean hovered = mouseX >= x && mouseX < x + SWATCH - 2 && mouseY >= y && mouseY < y + SWATCH - 2;
			context.drawBorder(x, y, SWATCH - 2, SWATCH - 2,
					hovered ? 0xFFFFFFFF : i == nearest ? 0xFFE8D8A0 : 0xFF000000);
		}

		context.drawText(textRenderer, Text.translatable("roleplayersquill.colour.hex"),
				panelX + 12, panelY + 2 * SWATCH + 32, 0xFFB0B0B0, false);

		Text note;
		if (typed < 0) {
			note = Text.translatable("roleplayersquill.colour.hex.hint").formatted(Formatting.DARK_GRAY);
		} else if (BookSender.canWriteRich()) {
			note = Text.translatable("roleplayersquill.colour.exact").formatted(Formatting.GREEN);
		} else {
			note = Text.translatable("roleplayersquill.colour.rounded",
					String.format(Locale.ROOT, "#%06X", QuillStyle.LEGACY_COLORS[Math.max(0, nearest)]
							.getColorValue() == null ? 0 : QuillStyle.LEGACY_COLORS[Math.max(0, nearest)].getColorValue()))
					.formatted(Formatting.GOLD);
		}
		context.drawText(textRenderer, note, panelX + 12, panelY + panelHeight - 66, 0xFFFFFFFF, false);
	}
}
