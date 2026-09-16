package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** A panel over the editor: the same frame for the colour, link, symbol, table and page dialogs. */
public abstract class DialogScreen extends Screen {
	protected static final int PANEL = 0xE0181818;
	protected static final int EDGE = 0xFF000000;
	protected static final int HEADING = 0xFFE8D8A0;

	@Nullable
	protected final Screen parent;

	protected int panelWidth = 260;
	protected int panelHeight = 180;
	protected int panelX;
	protected int panelY;

	protected DialogScreen(@Nullable Screen parent, Text title) {
		super(title);
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 8, HEADING);
	}

	/**
	 * Leaves whatever this was opened from on show behind it.
	 *
	 * <p>These dialogs are opened from somewhere you are working – the chat, a sign, a book – and
	 * hiding that is hiding the thing the dialog is about to change. So the screen underneath is
	 * drawn first, with the cursor parked off it so nothing under there lights up, and the panel
	 * goes on top.
	 */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		if (parent != null) {
			try {
				parent.render(context, -1, -1, delta);
			} catch (Throwable error) {
				// A screen that will not draw itself out of turn is not worth losing the dialog over.
				renderInGameBackground(context);
			}
		} else {
			renderInGameBackground(context);
		}
		// A dim of our own over all of it. Some screens draw no background at all – a sign editor
		// shows the world as it is – and a panel floating over an undimmed sunny field is hard to
		// read and looks like it belongs to something else.
		context.fill(0, 0, width, height, 0x90000000);
		context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
		context.drawBorder(panelX, panelY, panelWidth, panelHeight, EDGE);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
