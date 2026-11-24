package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.input.MouseButtonEvent;

import com.glamardor.roleplayersquill.book.BookSender;
import com.glamardor.roleplayersquill.text.QuillStyle;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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
	private EditBox hexField;
	private int typed = -1;

	public ColourScreen(@Nullable Screen parent, IntConsumer whenPicked) {
		super(parent, Component.translatable("roleplayersquill.colour.title"));
		this.whenPicked = whenPicked;
		this.panelWidth = 8 * SWATCH + 24;
		this.panelHeight = 2 * SWATCH + 104;
	}

	@Override
	protected void init() {
		super.init();

		hexField = new EditBox(font, panelX + 12, panelY + 2 * SWATCH + 44,
				panelWidth - 24, 18, Component.translatable("roleplayersquill.colour.hex"));
		hexField.setMaxLength(7);
		hexField.setHint(Component.literal("#7B4FA0").withStyle(ChatFormatting.DARK_GRAY));
		hexField.setResponder(value -> typed = parse(value));
		addRenderableWidget(hexField);

		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.colour.default"),
						button -> pick(QuillStyle.INHERIT))
				.bounds(panelX + 12, panelY + panelHeight - 50, panelWidth / 2 - 16, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.colour.apply"),
						button -> {
							if (typed >= 0) {
								pick(typed);
							}
						})
				.bounds(panelX + panelWidth / 2 + 4, panelY + panelHeight - 50, panelWidth / 2 - 16, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
				.bounds(panelX + panelWidth / 2 - 50, panelY + panelHeight - 26, 100, 20).build());
	}

	private void pick(int colour) {
		whenPicked.accept(colour);
		onClose();
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
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		int button = Input.button(event);
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int index = swatchAt(mouseX, mouseY);
		if (index >= 0) {
			Integer value = QuillStyle.rgb(QuillStyle.LEGACY_COLORS[index]);
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
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		int gridX = panelX + 12;
		int gridY = panelY + 22;
		int nearest = typed >= 0 ? QuillStyle.PLAIN.withColor(typed).legacyColorIndex() : -1;
		for (int i = 0; i < 16; i++) {
			int x = gridX + (i % 8) * SWATCH;
			int y = gridY + (i / 8) * SWATCH;
			Integer value = QuillStyle.rgb(QuillStyle.LEGACY_COLORS[i]);
			context.fill(x, y, x + SWATCH - 2, y + SWATCH - 2, 0xFF000000 | (value == null ? 0 : value));
			boolean hovered = mouseX >= x && mouseX < x + SWATCH - 2 && mouseY >= y && mouseY < y + SWATCH - 2;
			context.outline(x, y, SWATCH - 2, SWATCH - 2,
					hovered ? 0xFFFFFFFF : i == nearest ? 0xFFE8D8A0 : 0xFF000000);
		}

		context.text(font, Component.translatable("roleplayersquill.colour.hex"),
				panelX + 12, panelY + 2 * SWATCH + 32, 0xFFB0B0B0, false);

		Component note;
		if (typed < 0) {
			note = Component.translatable("roleplayersquill.colour.hex.hint").withStyle(ChatFormatting.DARK_GRAY);
		} else if (BookSender.canWriteRich()) {
			note = Component.translatable("roleplayersquill.colour.exact").withStyle(ChatFormatting.GREEN);
		} else {
			note = Component.translatable("roleplayersquill.colour.rounded",
					String.format(Locale.ROOT, "#%06X", QuillStyle.rgb(QuillStyle.LEGACY_COLORS[Math.max(0, nearest)]) == null
							? 0 : QuillStyle.rgb(QuillStyle.LEGACY_COLORS[Math.max(0, nearest)])))
					.withStyle(ChatFormatting.GOLD);
		}
		context.text(font, note, panelX + 12, panelY + panelHeight - 66, 0xFFFFFFFF, false);
	}
}
