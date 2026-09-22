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
	private final BookView view;

	private boolean keepCodes = true;
	private TextFieldWidget separator;
	/** What to call the file. Empty is the book's own title with the date after it. */
	private TextFieldWidget name;
	@Nullable
	private Path written;

	public ExportScreen(@Nullable Screen parent, BookView view) {
		super(parent, Text.translatable("roleplayersquill.export.title"));
		this.view = view;
		this.panelWidth = 300;
		this.panelHeight = 222;
	}

	@Override
	protected void init() {
		super.init();

		// The name first, because it is the one thing that has to be settled before the file is
		// written and the one thing the old screen had no room for: an untitled book went out as
		// book-2026-09-22-153000.json, which is a name only a file system could love.
		String was = name == null ? initialName() : name.getText();
		name = new TextFieldWidget(textRenderer, panelX + 12, panelY + 36, panelWidth - 24, 18,
				Text.translatable("roleplayersquill.export.name"));
		name.setMaxLength(64);
		name.setPlaceholder(Text.translatable("roleplayersquill.export.name.auto")
				.formatted(Formatting.DARK_GRAY));
		name.setText(was);
		addDrawableChild(name);
		setInitialFocus(name);

		separator = new TextFieldWidget(textRenderer, panelX + 12, panelY + 70, panelWidth - 24, 18,
				Text.translatable("roleplayersquill.export.separator"));
		separator.setMaxLength(32);
		separator.setText("---");
		addDrawableChild(separator);

		addDrawableChild(ButtonWidget.builder(codesLabel(), button -> {
			keepCodes = !keepCodes;
			button.setMessage(codesLabel());
		}).dimensions(panelX + 12, panelY + 96, panelWidth - 24, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.export.text"), button -> exportText())
				.dimensions(panelX + 12, panelY + 124, panelWidth - 24, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.export.document"), button -> exportDocument())
				.dimensions(panelX + 12, panelY + 148, panelWidth - 24, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.export.folder"), button -> {
			FileDialogs.reveal(BookIO.exportDir());
		}).dimensions(panelX + 12, panelY + panelHeight - 28, 140, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(panelX + 158, panelY + panelHeight - 28, 130, 20).build());
	}

	/** The book's own name to start from: its title, or the line it opens with. */
	private String initialName() {
		String label = BookIO.nameOf(view.document());
		return label.endsWith("…") ? label.substring(0, label.length() - 1).strip() : label;
	}

	private Text codesLabel() {
		return Text.translatable(keepCodes
				? "roleplayersquill.export.codes.keep"
				: "roleplayersquill.export.codes.strip");
	}

	private void exportText() {
		String suggested = BookIO.fileNameFor(name.getText(), view.document().title(), "txt");
		FileDialogs.save(Text.translatable("roleplayersquill.export.text").getString(), suggested,
				new String[] { "*.txt" }, "Text", path -> {
					try {
						Path target = path == null ? null : path;
						if (target == null) {
							return;
						}
						Path produced = BookIO.exportText(view.document(), view.encodePages(),
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
		String suggested = BookIO.fileNameFor(name.getText(), view.document().title(), "json");
		FileDialogs.save(Text.translatable("roleplayersquill.export.document").getString(), suggested,
				new String[] { "*.json" }, "Roleplayer's Quill", path -> {
					if (path == null) {
						return;
					}
					try {
						BookIO.writeDocument(view.document(), path);
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
		context.drawText(textRenderer, Text.translatable("roleplayersquill.export.name"),
				panelX + 12, panelY + 26, 0xFF9A9A9A, false);
		context.drawText(textRenderer, Text.translatable("roleplayersquill.export.separator"),
				panelX + 12, panelY + 60, 0xFF9A9A9A, false);
		if (written != null) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("roleplayersquill.export.written", written.getFileName().toString())
							.formatted(Formatting.GREEN),
					width / 2, panelY + 176, 0xFFFFFFFF);
		}
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
