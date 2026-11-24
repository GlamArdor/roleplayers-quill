package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.input.KeyEvent;

import com.glamardor.roleplayersquill.text.QuillDocument;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * One line, typed: what a book on the shelf is to be called from now on.
 *
 * <p>A window rather than a box that opens inside the row, because the row is twenty-six pixels
 * tall and already carries a name, a date, a star and a pencil. Small enough to read as the
 * question it is.
 */
public class RenameScreen extends DialogScreen {
	private final String initial;
	private final Consumer<String> whenNamed;
	private EditBox name;

	public RenameScreen(@Nullable Screen parent, String initial, Consumer<String> whenNamed) {
		super(parent, Component.translatable("roleplayersquill.shelf.rename.title"));
		this.initial = initial;
		this.whenNamed = whenNamed;
		this.panelWidth = 280;
		this.panelHeight = 92;
	}

	@Override
	protected void init() {
		super.init();
		String was = name == null ? initial : name.getValue();
		name = new EditBox(font, panelX + 12, panelY + 32, panelWidth - 24, 18,
				Component.translatable("roleplayersquill.shelf.rename.title"));
		// The same ceiling a book's own title has: this name is what goes onto the book when it is
		// restored and signed, so a name that would not fit there should not be typed here.
		name.setMaxLength(QuillDocument.MAX_TITLE);
		name.setValue(was);
		name.moveCursorToEnd(false);
		addRenderableWidget(name);
		setInitialFocus(name);

		int y = panelY + panelHeight - 28;
		int half = (panelWidth - 28) / 2;
		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.shelf.rename.save"),
						b -> save())
				.bounds(panelX + 12, y, half, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(panelX + panelWidth - 12 - half, y, half, 20).build());
	}

	private void save() {
		whenNamed.accept(name.getValue().strip());
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key(), scanCode = event.keycode(), modifiers = event.modifiers();
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			save();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.text(font, Component.translatable("roleplayersquill.shelf.rename.hint"),
				panelX + 12, panelY + 22, 0xFF9A9A9A, false);
	}
}
