package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookSender;
import com.glamardor.roleplayersquill.text.QuillStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The colour picker, hanging under the button that opens it.
 *
 * <p>It used to be a screen of its own, and a screen of its own is the wrong shape for choosing a
 * colour: the page disappears, and the page is the thing the colour is going on. Sixteen swatches
 * and a box for anything else fit in a panel the size of a tooltip, so they live in one.
 *
 * <p>The sixteen are the colours a {@code §} code can name, which is to say the ones every reader
 * will see. A colour typed into the box is kept exactly as typed and sent as a text component if the
 * book can be written that way; if it cannot, it is rounded to the nearest of the sixteen, and the
 * swatch it would round to is marked so the compromise is visible before it is made.
 */
public final class ColourPopup {
	private static final int WIDTH = 132;
	private static final int SWATCH = 15;
	private static final int COLUMNS = 8;

	private final IntConsumer whenPicked;
	private final Runnable whenClosed;

	private TextFieldWidget hex;
	private int typed = -1;
	private int x;
	private int y;

	public ColourPopup(IntConsumer whenPicked, Runnable whenClosed) {
		this.whenPicked = whenPicked;
		this.whenClosed = whenClosed;
	}

	public static int width() {
		return WIDTH;
	}

	public int height() {
		return 18 + 2 * SWATCH + 44;
	}

	/**
	 * Puts the panel under the button that opened it, and keeps it on the screen.
	 *
	 * @param adder how the host screen takes a widget, since only it can
	 */
	public void layout(int anchorX, int anchorY, int screenWidth, int screenHeight,
			Consumer<ClickableWidget> adder, TextRenderer textRenderer) {
		x = Math.max(2, Math.min(anchorX, screenWidth - WIDTH - 2));
		y = Math.max(2, Math.min(anchorY, screenHeight - height() - 2));

		hex = new TextFieldWidget(textRenderer, x + 6, y + 20 + 2 * SWATCH, WIDTH - 12, 14,
				Text.translatable("roleplayersquill.colour.hex"));
		hex.setMaxLength(7);
		hex.setPlaceholder(Text.literal("#7B4FA0").formatted(Formatting.DARK_GRAY));
		hex.setChangedListener(value -> typed = parse(value));
		adder.accept(hex);

		adder.accept(ButtonWidget.builder(Text.translatable("roleplayersquill.colour.apply"), button -> {
			if (typed >= 0) {
				pick(typed);
			}
		}).dimensions(x + 6, y + 38 + 2 * SWATCH, WIDTH / 2 - 8, 14).build());

		adder.accept(ButtonWidget.builder(Text.translatable("roleplayersquill.colour.ink"),
				button -> pick(QuillStyle.INHERIT))
				.dimensions(x + WIDTH / 2 + 2, y + 38 + 2 * SWATCH, WIDTH / 2 - 8, 14).build());
	}

	/** True while the box has the keyboard, so the host knows not to type into the book. */
	public boolean typing(Object focused) {
		return focused == hex;
	}

	private void pick(int colour) {
		whenPicked.accept(colour);
		whenClosed.run();
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

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + height();
	}

	private int swatchAt(double mouseX, double mouseY) {
		for (int i = 0; i < 16; i++) {
			int sx = x + 6 + (i % COLUMNS) * SWATCH;
			int sy = y + 16 + (i / COLUMNS) * SWATCH;
			if (mouseX >= sx && mouseX < sx + SWATCH - 1 && mouseY >= sy && mouseY < sy + SWATCH - 1) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Takes a click on a swatch.
	 *
	 * @return false when the click landed inside the panel but not on a swatch, so that the box and
	 *         the two buttons – which are ordinary widgets – still get their turn
	 */
	public boolean pickAt(double mouseX, double mouseY) {
		int index = swatchAt(mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		Integer value = QuillStyle.LEGACY_COLORS[index].getColorValue();
		pick(value == null ? QuillStyle.INHERIT : value);
		return true;
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		context.fill(x, y, x + WIDTH, y + height(), 0xF0181818);
		context.drawBorder(x, y, WIDTH, height(), 0xFF000000);
		context.drawText(textRenderer, Text.translatable("roleplayersquill.colour.title"),
				x + 6, y + 5, 0xFFE8D8A0, false);

		int nearest = typed >= 0 ? QuillStyle.PLAIN.withColor(typed).legacyColorIndex() : -1;
		for (int i = 0; i < 16; i++) {
			int sx = x + 6 + (i % COLUMNS) * SWATCH;
			int sy = y + 16 + (i / COLUMNS) * SWATCH;
			Integer value = QuillStyle.LEGACY_COLORS[i].getColorValue();
			context.fill(sx, sy, sx + SWATCH - 1, sy + SWATCH - 1, 0xFF000000 | (value == null ? 0 : value));
			boolean hovered = mouseX >= sx && mouseX < sx + SWATCH - 1 && mouseY >= sy && mouseY < sy + SWATCH - 1;
			context.drawBorder(sx, sy, SWATCH - 1, SWATCH - 1,
					hovered ? 0xFFFFFFFF : i == nearest ? 0xFFE8D8A0 : 0xFF000000);
		}

		Text note;
		if (typed < 0) {
			note = Text.translatable("roleplayersquill.colour.hex.hint").formatted(Formatting.DARK_GRAY);
		} else if (BookSender.canWriteRich()) {
			note = Text.translatable("roleplayersquill.colour.exact.short").formatted(Formatting.GREEN);
		} else {
			note = Text.translatable("roleplayersquill.colour.rounded.short").formatted(Formatting.GOLD);
		}
		context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(note.getString(), WIDTH - 12))
				.setStyle(note.getStyle()), x + 6, y + height() - 12, 0xFFFFFFFF, false);
	}

	@Nullable
	public TextFieldWidget field() {
		return hex;
	}
}
