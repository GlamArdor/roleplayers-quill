package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookSender;
import com.glamardor.roleplayersquill.text.QuillStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Hangs a link, a tooltip or a command on the selected words.
 *
 * <p>How much of it survives depends on how the book can be written, and the screen says which
 * before anything is typed rather than after the book is signed. Written as a text component – which
 * creative mode allows and nothing else does – all of it survives and every reader gets it, mod or
 * no mod. Written as {@code §} codes, none of it can be carried, and the address is instead left in
 * the text where a reader with this mod can click it and a reader without can at least see it.
 */
public class LinkScreen extends DialogScreen {
	private final PageEditor editor;

	private EditBox url;
	private EditBox tooltip;
	private EditBox command;
	private EditBox page;
	private boolean showAddress = true;

	public LinkScreen(@Nullable Screen parent, PageEditor editor) {
		super(parent, Component.translatable("roleplayersquill.link.title"));
		this.editor = editor;
		this.panelWidth = 300;
		this.panelHeight = 224;
	}

	@Override
	protected void init() {
		super.init();
		QuillStyle current = editor.activeStyle();

		url = field(panelY + 37, current.url(), "roleplayersquill.link.url");
		tooltip = field(panelY + 71, current.hover(), "roleplayersquill.link.tooltip");
		command = field(panelY + 105, current.command(), "roleplayersquill.link.command");
		page = field(panelY + 139, current.page() > 0 ? String.valueOf(current.page()) : null,
				"roleplayersquill.link.page");
		page.setMaxLength(3);

		Button addressToggle = Button.builder(addressLabel(), button -> {
			showAddress = !showAddress;
			button.setMessage(addressLabel());
		}).bounds(panelX + 12, panelY + 162, panelWidth - 24, 18).build();
		addRenderableWidget(addressToggle);

		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.link.apply"), button -> apply())
				.bounds(panelX + 12, panelY + panelHeight - 28, 110, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.link.remove"), button -> remove())
				.bounds(panelX + 128, panelY + panelHeight - 28, 70, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
				.bounds(panelX + 204, panelY + panelHeight - 28, 84, 20).build());
	}

	private Component addressLabel() {
		return Component.translatable(showAddress
				? "roleplayersquill.link.show_address.on"
				: "roleplayersquill.link.show_address.off");
	}

	private EditBox field(int y, @Nullable String value, String key) {
		EditBox widget = new EditBox(font, panelX + 12, y, panelWidth - 24, 18,
				Component.translatable(key));
		widget.setMaxLength(256);
		if (value != null) {
			widget.setValue(value);
		}
		addRenderableWidget(widget);
		return widget;
	}

	private void apply() {
		String address = blankToNull(url.getValue());
		String hover = blankToNull(tooltip.getValue());
		String run = blankToNull(command.getValue());
		int jump = 0;
		try {
			jump = page.getValue().isBlank() ? 0 : Integer.parseInt(page.getValue().trim());
		} catch (NumberFormatException ignored) {
			// An unreadable page number is no page number.
		}

		if (!editor.hasSelection() && address != null && showAddress) {
			// Nothing is selected, so there is nothing to hang the link on: put the address in, and
			// hang it on that.
			editor.insertWithStyle(address, QuillStyle.PLAIN
					.withUrl(address)
					.withHover(hover)
					.withCommand(run));
			onClose();
			return;
		}

		int finalJump = jump;
		editor.restyle(style -> style
				.withUrl(address)
				.withHover(hover)
				.withCommand(run)
				.withPage(finalJump));

		if (address != null && showAddress && !BookSender.canWriteRich() && !selectionShows(address)) {
			// The component cannot be sent, so the address goes in the text where it is at least
			// visible – and clickable for anyone else running this mod. Unless the words it was
			// hung on are the address already, in which case writing it again is just writing it
			// twice.
			editor.insert(" (" + address + ")");
		}
		onClose();
	}

	/** Whether the words the link was hung on already say the address. */
	private boolean selectionShows(String address) {
		String selected = editor.selectedText(false).trim();
		if (selected.isEmpty()) {
			return false;
		}
		String bare = address.replaceFirst("^[a-zA-Z]+://", "").replaceFirst("^www\\.", "");
		return selected.contains(bare) || address.contains(selected);
	}

	private void remove() {
		editor.restyle(QuillStyle::withoutInteraction);
		onClose();
	}

	@Nullable
	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		Component status = BookSender.canWriteRich()
				? Component.translatable("roleplayersquill.link.rich").withStyle(ChatFormatting.GREEN)
				: Component.translatable("roleplayersquill.link.plain").withStyle(ChatFormatting.GOLD);
		context.centeredText(font, status, width / 2, panelY + 20, 0xFFFFFFFF);

		label(context, "roleplayersquill.link.url", panelY + 26);
		label(context, "roleplayersquill.link.tooltip", panelY + 60);
		label(context, "roleplayersquill.link.command", panelY + 94);
		label(context, "roleplayersquill.link.page", panelY + 128);
	}

	private void label(GuiGraphicsExtractor context, String key, int y) {
		context.text(font, Component.translatable(key), panelX + 12, y, 0xFF9A9A9A, false);
	}
}
