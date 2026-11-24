package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.book.BookSender;
import com.glamardor.roleplayersquill.text.QuillDocument;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Signing: the last thing that happens to a book, and the one that cannot be undone.
 *
 * <p>It is also where the two ways of writing a page part company, so the screen says which one is
 * about to be used and what that costs. In creative the book is handed over as a finished item and
 * keeps its links, its tooltips and its exact colours. Everywhere else it goes as strings, and
 * anything a {@code §} code cannot say is lost – which the player is told before pressing the
 * button, not after.
 */
public class SignBookScreen extends DialogScreen {
	private final PageEditor editor;
	private final ItemStack stack;
	private final InteractionHand hand;
	private EditBox titleField;
	/** The book this came from, where a page too big to send has to be shown. */
	@Nullable
	private final QuillEditScreen book;

	public SignBookScreen(@Nullable Screen parent, PageEditor editor, ItemStack stack, InteractionHand hand) {
		super(parent, Component.translatable("roleplayersquill.sign.title"));
		this.book = parent instanceof QuillEditScreen screen ? screen : null;
		this.editor = editor;
		this.stack = stack;
		this.hand = hand;
		this.panelWidth = 300;
		this.panelHeight = 176;
	}

	@Override
	protected void init() {
		super.init();

		titleField = new EditBox(font, panelX + 12, panelY + 36, panelWidth - 24, 20,
				Component.translatable("roleplayersquill.sign.name"));
		titleField.setMaxLength(QuillDocument.MAX_TITLE);
		titleField.setValue(editor.document().title());
		titleField.setResponder(value -> editor.document().setTitle(value));
		addRenderableWidget(titleField);
		setInitialFocus(titleField);

		addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.sign.confirm"), button -> sign())
				.bounds(panelX + 12, panelY + panelHeight - 28, 140, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
				.bounds(panelX + 158, panelY + panelHeight - 28, 130, 20).build());
	}

	private void sign() {
		Minecraft client = Minecraft.getInstance();
		editor.document().trimTrailingEmptyPages();
		// Signing cannot be taken back, so a page that will not survive the crossing stops it here.
		// The editor turns to that page and says which one it is.
		if (book != null && !book.checkPagesFit()) {
			client.gui.setScreen(book);
			return;
		}
		String name = editor.document().title().isBlank()
				? Component.translatable("roleplayersquill.sign.untitled").getString()
				: editor.document().title();

		// Kept beside the book whichever way it goes out, so that a copy of it can be opened and
		// edited again with everything this editor knows about it.
		List<String> encoded = editor.encodePages();
		BookIO.saveDraft(editor.document(), encoded, "");

		if (editor.needsRich() && BookSender.canWriteRich()) {
			BookSender.signRich(hand, editor.encodeRichPages(), name, 0);
		} else {
			BookSender.sign(stack, hand, encoded, name);
		}
		client.gui.setScreen(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		context.text(font, Component.translatable("roleplayersquill.sign.name"),
				panelX + 12, panelY + 26, 0xFF9A9A9A, false);

		Minecraft client = Minecraft.getInstance();
		String author = client.player == null ? "" : client.player.getGameProfile().name();
		context.text(font, Component.translatable("book.byAuthor", author).withStyle(ChatFormatting.GRAY),
				panelX + 12, panelY + 62, 0xFFFFFFFF, false);

		Component mode;
		if (!editor.needsRich()) {
			mode = Component.translatable("roleplayersquill.sign.plain_enough").withStyle(ChatFormatting.GRAY);
		} else if (BookSender.canWriteRich()) {
			mode = Component.translatable("roleplayersquill.sign.rich").withStyle(ChatFormatting.GREEN);
		} else {
			mode = Component.translatable("roleplayersquill.sign.degrading").withStyle(ChatFormatting.GOLD);
		}
		context.text(font, mode, panelX + 12, panelY + 80, 0xFFFFFFFF, false);

		context.textWithWordWrap(font, Component.translatable("book.finalizeWarning"),
				panelX + 12, panelY + 100, panelWidth - 24, 0xFFB0B0B0, false);
	}
}
