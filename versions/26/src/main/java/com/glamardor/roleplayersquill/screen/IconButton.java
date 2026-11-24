package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * One square of the toolbar.
 *
 * <p>Small, quiet, and able to show that it is on – the bold button has to look bold while the
 * caret is inside bold text, or the toolbar is telling the writer nothing.
 */
public class IconButton extends AbstractWidget {
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

	public IconButton(int x, int y, Icons.Icon icon, Component tooltip, Runnable action) {
		super(x, y, SIZE, SIZE, tooltip);
		this.icon = icon;
		this.action = action;
		setTooltip(Tooltip.create(tooltip));
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
	private java.util.function.Supplier<Component> tip;

	/**
	 * Gives the button a tooltip that is worked out each time it is shown.
	 *
	 * <p>For the ones that can be pressed and still not do what they are for – dictation with the
	 * voice switched off, say. Saying so on hover means the reason arrives before the press rather
	 * than after it.
	 */
	public IconButton telling(java.util.function.Supplier<Component> tooltip) {
		this.tip = tooltip;
		return this;
	}


	/**
	 * Never takes the focus from the keyboard.
	 *
	 * <p>Since 1.21.6 a screen moves the focus with the arrow keys, and the chat asks it to before it
	 * looks for the up arrow itself – so with this row over the chat box, the up arrow that should
	 * bring back the last message walked onto a button and left its tooltip over the chat. The
	 * buttons are for the mouse; the arrows belong to whatever is being typed in.
	 */
	@Override
	public net.minecraft.client.gui.ComponentPath nextFocusPath(
			net.minecraft.client.gui.navigation.FocusNavigationEvent navigation) {
		return null;
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
	protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		if (tip != null) {
			setTooltip(Tooltip.create(tip.get()));
		}
		boolean usable = enabled.getAsBoolean();
		boolean on = active.getAsBoolean();
		int face = !usable ? FACE_OFF : on ? FACE_ON : isHovered() ? FACE_HOVER : FACE;
		context.fill(getX(), getY(), getX() + width, getY() + height, face);
		context.outline(getX(), getY(), width, height, BORDER);
		icon.draw(context, getX() + 1, getY() + 1, usable ? markColor : MARK_DIM);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
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
	protected void updateWidgetNarration(NarrationElementOutput builder) {
		defaultButtonNarrationText(builder);
	}
}
