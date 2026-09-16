package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/**
 * One square of the toolbar.
 *
 * <p>Small, quiet, and able to show that it is on – the bold button has to look bold while the
 * caret is inside bold text, or the toolbar is telling the writer nothing.
 */
public class IconButton extends ClickableWidget {
	public static final int SIZE = 18;

	private static final int BORDER = 0xFF1A1A1A;
	private static final int FACE = 0xC0303030;
	private static final int FACE_HOVER = 0xE0505050;
	private static final int FACE_ON = 0xFF3C6390;
	private static final int FACE_OFF = 0x60202020;
	private static final int MARK = 0xFFE8E8E8;
	private static final int MARK_DIM = 0xFF808080;

	private final Icons.Icon icon;
	private final Runnable action;
	private BooleanSupplier active = () -> false;
	private BooleanSupplier enabled = () -> true;
	private int markColor = MARK;

	public IconButton(int x, int y, Icons.Icon icon, Text tooltip, Runnable action) {
		super(x, y, SIZE, SIZE, tooltip);
		this.icon = icon;
		this.action = action;
		setTooltip(Tooltip.of(tooltip));
	}

	/** Tells the button when to look pressed in. */
	public IconButton showing(BooleanSupplier active) {
		this.active = active;
		return this;
	}

	public IconButton onlyWhen(BooleanSupplier enabled) {
		this.enabled = enabled;
		return this;
	}

	/** What the tooltip says now, for buttons whose answer depends on a setting. */
	private java.util.function.Supplier<Text> tip;

	/**
	 * Gives the button a tooltip that is worked out each time it is shown.
	 *
	 * <p>For the ones that can be pressed and still not do what they are for – dictation with the
	 * voice switched off, say. Saying so on hover means the reason arrives before the press rather
	 * than after it.
	 */
	public IconButton telling(java.util.function.Supplier<Text> tooltip) {
		this.tip = tooltip;
		return this;
	}


	/** Used by the colour button, which draws its icon in whatever colour is chosen. */
	public IconButton inColour(int rgb) {
		this.markColor = 0xFF000000 | rgb;
		return this;
	}

	public void setColour(int rgb) {
		this.markColor = 0xFF000000 | rgb;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		if (tip != null) {
			setTooltip(Tooltip.of(tip.get()));
		}
		boolean usable = enabled.getAsBoolean();
		boolean on = active.getAsBoolean();
		int face = !usable ? FACE_OFF : on ? FACE_ON : isHovered() ? FACE_HOVER : FACE;
		context.fill(getX(), getY(), getX() + width, getY() + height, face);
		context.drawBorder(getX(), getY(), width, height, BORDER);
		icon.draw(context, getX() + 1, getY() + 1, usable ? markColor : MARK_DIM);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		if (enabled.getAsBoolean()) {
			action.run();
		}
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return this.visible && mouseX >= getX() && mouseY >= getY()
				&& mouseX < getX() + width && mouseY < getY() + height;
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}
}
