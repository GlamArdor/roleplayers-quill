package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.QuillDocument;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

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
	private TextFieldWidget name;

	public RenameScreen(@Nullable Screen parent, String initial, Consumer<String> whenNamed) {
		super(parent, Text.translatable("roleplayersquill.shelf.rename.title"));
		this.initial = initial;
		this.whenNamed = whenNamed;
		this.panelWidth = 280;
		this.panelHeight = 92;
	}

	@Override
	protected void init() {
		super.init();
		String was = name == null ? initial : name.getText();
		name = new TextFieldWidget(textRenderer, panelX + 12, panelY + 32, panelWidth - 24, 18,
				Text.translatable("roleplayersquill.shelf.rename.title"));
		// The same ceiling a book's own title has: this name is what goes onto the book when it is
		// restored and signed, so a name that would not fit there should not be typed here.
		name.setMaxLength(QuillDocument.MAX_TITLE);
		name.setText(was);
		name.setCursorToEnd(false);
		addDrawableChild(name);
		setInitialFocus(name);

		int y = panelY + panelHeight - 28;
		int half = (panelWidth - 28) / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.shelf.rename.save"),
						b -> save())
				.dimensions(panelX + 12, y, half, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
				.dimensions(panelX + panelWidth - 12 - half, y, half, 20).build());
	}

	private void save() {
		whenNamed.accept(name.getText().strip());
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			save();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawText(textRenderer, Text.translatable("roleplayersquill.shelf.rename.hint"),
				panelX + 12, panelY + 22, 0xFF9A9A9A, false);
	}
}
