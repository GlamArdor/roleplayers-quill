package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.book.FileDialogs;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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
	private EditBox separator;
	/** What to call the file. Empty is the book's own title with the date after it. */
	private EditBox name;
	@Nullable
	private Path written;

	public ExportScreen(@Nullable Screen parent, BookView view) {
		super(parent, Component.translatable("roleplayersquill.export.title"));
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
		String was = name == null ? initialName() : name.getValue();
		name = new EditBox(font, panelX + 12, panelY + 36, panelWidth - 24, 18,
				Component.translatable("roleplayersquill.export.name"));
		name.setMaxLength(64);
		name.setHint(Component.translatable("roleplayersquill.export.name.auto")
				.withStyle(ChatFormatting.DARK_GRAY));
		name.setValue(was);
		addRenderableWidget(name);
		setInitialFocus(name);

		separator = new EditBox(font, panelX + 12, panelY + 70, panelWidth - 24, 18,
				Component.translatable("roleplayersquill.export.separator"));
		separator.setMaxLength(32);
		separator.setValue("---");
		addRenderableWidget(separator);

		addRenderableWidget(Button.builder(codesLabel(), button -> {
			keepCodes = !keepCodes;
			button.setMessage(codesLabel());
		}).bounds(panelX + 12, panelY + 96, panelWidth - 24, 18).build());

		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.export.text"), button -> exportText())
				.bounds(panelX + 12, panelY + 124, panelWidth - 24, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.export.document"), button -> exportDocument())
				.bounds(panelX + 12, panelY + 148, panelWidth - 24, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.export.folder"), button -> {
			FileDialogs.reveal(BookIO.exportDir());
		}).bounds(panelX + 12, panelY + panelHeight - 28, 140, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(panelX + 158, panelY + panelHeight - 28, 130, 20).build());
	}

	/** The book's own name to start from: its title, or the line it opens with. */
	private String initialName() {
		String label = BookIO.nameOf(view.document());
		return label.endsWith("…") ? label.substring(0, label.length() - 1).strip() : label;
	}

	private Component codesLabel() {
		return Component.translatable(keepCodes
				? "roleplayersquill.export.codes.keep"
				: "roleplayersquill.export.codes.strip");
	}

	private void exportText() {
		String suggested = BookIO.fileNameFor(name.getValue(), view.document().title(), "txt");
		FileDialogs.save(Component.translatable("roleplayersquill.export.text").getString(), suggested,
				new String[] { "*.txt" }, "Text", path -> {
					try {
						Path target = path == null ? null : path;
						if (target == null) {
							return;
						}
						Path produced = BookIO.exportText(view.document(), view.encodePages(),
								keepCodes, separator.getValue());
						Files.move(produced, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
						written = target;
					} catch (IOException error) {
						RoleplayersQuill.LOGGER.warn("Could not export the book", error);
						written = null;
					}
				});
	}

	private void exportDocument() {
		String suggested = BookIO.fileNameFor(name.getValue(), view.document().title(), "json");
		FileDialogs.save(Component.translatable("roleplayersquill.export.document").getString(), suggested,
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
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.text(font, Component.translatable("roleplayersquill.export.name"),
				panelX + 12, panelY + 26, 0xFF9A9A9A, false);
		context.text(font, Component.translatable("roleplayersquill.export.separator"),
				panelX + 12, panelY + 60, 0xFF9A9A9A, false);
		if (written != null) {
			context.centeredText(font,
					Component.translatable("roleplayersquill.export.written", written.getFileName().toString())
							.withStyle(ChatFormatting.GREEN),
					width / 2, panelY + 176, 0xFFFFFFFF);
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}
}
