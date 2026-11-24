package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

	protected DialogScreen(@Nullable Screen parent, Component title) {
		super(title);
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelX = (width - panelWidth) / 2;
		panelY = panelTop();
	}

	/**
	 * Where the panel's top edge goes. The middle of the screen, unless a dialog has a reason.
	 *
	 * <p>Searching a book has one: the panel would sit over the very page it is finding things on.
	 */
	protected int panelTop() {
		return (height - panelHeight) / 2;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, panelY + 8, HEADING);
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
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		if (parent != null) {
			try {
				// A vanilla book draws its page – the texture itself – in renderBackground, and
				// Screen.render never calls that: the game calls the two of them in turn, and only
				// the second of them is what a dialog standing over a screen reaches for. So the
				// book is asked for its background as well, or the panel opens over a page of text
				// hanging in mid air with the world showing through where the book should be.
				if (parent instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen) {
					parent.extractBackground(context, -1, -1, delta);
				}
				parent.extractRenderState(context, -1, -1, delta);
			} catch (Throwable error) {
				// A screen that will not draw itself out of turn is not worth losing the dialog over.
				extractTransparentBackground(context);
			}
		} else {
			extractTransparentBackground(context);
		}
		// A dim of our own over all of it. Some screens draw no background at all – a sign editor
		// shows the world as it is – and a panel floating over an undimmed sunny field is hard to
		// read and looks like it belongs to something else.
		//
		// Never over a book. A book draws the dim itself before it draws its page, so a second one
		// lands on the paper rather than on the world: the page goes brown, and the reader, who
		// only asked for a list of chapters, watches the book they are reading go dark behind it.
		if (!(parent instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen)) {
			context.fill(0, 0, width, height, 0x90000000);
		}
		context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
		context.outline(panelX, panelY, panelWidth, panelHeight, EDGE);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}
}
