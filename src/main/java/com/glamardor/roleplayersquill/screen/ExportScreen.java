package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.book.FileDialogs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writing the book out to a file.
 *
 * <p>Two formats for two purposes: a text file to read, edit and hand to somebody, and the mod's
 * own format, which keeps the paragraphs, the alignment, the links and the colour of every
 * character so that the book can come back exactly as it went.
 */
public class ExportScreen extends DialogScreen {
	private final PageEditor editor;

	private boolean keepCodes = true;
	private TextFieldWidget separator;
	@Nullable
	private Path written;

	public ExportScreen(@Nullable Screen parent, PageEditor editor) {
		super(parent, Text.translatable("roleplayersquill.export.title"));
		this.editor = editor;
		this.panelWidth = 300;
		this.panelHeight = 190;
	}

	@Override
	protected void init() {
		super.init();

		separator = new TextFieldWidget(textRenderer, panelX + 12, panelY + 36, panelWidth - 24, 18,
				Text.translatable("roleplayersquill.export.separator"));
		separator.setMaxLength(32);
		separator.setText("---");
		addDrawableChild(separator);

		addDrawableChild(ButtonWidget.builder(codesLabel(), button -> {
			keepCodes = !keepCodes;
			button.setMessage(codesLabel());
		}).dimensions(panelX + 12, panelY + 62, panelWidth - 24, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.export.text"), button -> exportText())
				.dimensions(panelX + 12, panelY + 90, panelWidth - 24, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.export.document"), button -> exportDocument())
				.dimensions(panelX + 12, panelY + 114, panelWidth - 24, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.export.folder"), button -> {
			FileDialogs.reveal(BookIO.exportDir());
		}).dimensions(panelX + 12, panelY + panelHeight - 28, 140, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(panelX + 158, panelY + panelHeight - 28, 130, 20).build());
	}

	private Text codesLabel() {
		return Text.translatable(keepCodes
				? "roleplayersquill.export.codes.keep"
				: "roleplayersquill.export.codes.strip");
	}

	private void exportText() {
		String suggested = BookIO.suggestName(editor.document().title(), "txt");
		FileDialogs.save(Text.translatable("roleplayersquill.export.text").getString(), suggested,
				new String[] { "*.txt" }, "Text", path -> {
					try {
						Path target = path == null ? null : path;
						if (target == null) {
							return;
						}
						Path produced = BookIO.exportText(editor.document(), editor.encodePages(),
								keepCodes, separator.getText());
						Files.move(produced, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
						written = target;
					} catch (IOException error) {
						RoleplayersQuill.LOGGER.warn("Could not export the book", error);
						written = null;
					}
				});
	}

	private void exportDocument() {
		String suggested = BookIO.suggestName(editor.document().title(), "json");
		FileDialogs.save(Text.translatable("roleplayersquill.export.document").getString(), suggested,
				new String[] { "*.json" }, "Roleplayer's Quill", path -> {
					if (path == null) {
						return;
					}
					try {
						BookIO.writeDocument(editor.document(), path);
						written = path;
					} catch (IOException error) {
						RoleplayersQuill.LOGGER.warn("Could not export the book", error);
						written = null;
					}
				});
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawText(textRenderer, Text.translatable("roleplayersquill.export.separator"),
				panelX + 12, panelY + 26, 0xFF9A9A9A, false);
		if (written != null) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("roleplayersquill.export.written", written.getFileName().toString())
							.formatted(Formatting.GREEN),
					width / 2, panelY + 142, 0xFFFFFFFF);
		}
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
