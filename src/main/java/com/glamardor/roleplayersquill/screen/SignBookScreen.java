package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.book.BookSender;
import com.glamardor.roleplayersquill.text.QuillDocument;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
	private final Hand hand;
	private TextFieldWidget titleField;
	/** The book this came from, where a page too big to send has to be shown. */
	@Nullable
	private final QuillEditScreen book;

	public SignBookScreen(@Nullable Screen parent, PageEditor editor, ItemStack stack, Hand hand) {
		super(parent, Text.translatable("roleplayersquill.sign.title"));
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

		titleField = new TextFieldWidget(textRenderer, panelX + 12, panelY + 36, panelWidth - 24, 20,
				Text.translatable("roleplayersquill.sign.name"));
		titleField.setMaxLength(QuillDocument.MAX_TITLE);
		titleField.setText(editor.document().title());
		titleField.setChangedListener(value -> editor.document().setTitle(value));
		addDrawableChild(titleField);
		setInitialFocus(titleField);

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.sign.confirm"), button -> sign())
				.dimensions(panelX + 12, panelY + panelHeight - 28, 140, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
				.dimensions(panelX + 158, panelY + panelHeight - 28, 130, 20).build());
	}

	private void sign() {
		MinecraftClient client = MinecraftClient.getInstance();
		editor.document().trimTrailingEmptyPages();
		// Signing cannot be taken back, so a page that will not survive the crossing stops it here.
		// The editor turns to that page and says which one it is.
		if (book != null && !book.checkPagesFit()) {
			client.setScreen(book);
			return;
		}
		String name = editor.document().title().isBlank()
				? Text.translatable("roleplayersquill.sign.untitled").getString()
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
		client.setScreen(null);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		context.drawText(textRenderer, Text.translatable("roleplayersquill.sign.name"),
				panelX + 12, panelY + 26, 0xFF9A9A9A, false);

		MinecraftClient client = MinecraftClient.getInstance();
		String author = client.player == null ? "" : client.player.getGameProfile().getName();
		context.drawText(textRenderer, Text.translatable("book.byAuthor", author).formatted(Formatting.GRAY),
				panelX + 12, panelY + 62, 0xFFFFFFFF, false);

		Text mode;
		if (!editor.needsRich()) {
			mode = Text.translatable("roleplayersquill.sign.plain_enough").formatted(Formatting.GRAY);
		} else if (BookSender.canWriteRich()) {
			mode = Text.translatable("roleplayersquill.sign.rich").formatted(Formatting.GREEN);
		} else {
			mode = Text.translatable("roleplayersquill.sign.degrading").formatted(Formatting.GOLD);
		}
		context.drawText(textRenderer, mode, panelX + 12, panelY + 80, 0xFFFFFFFF, false);

		context.drawWrappedText(textRenderer, Text.translatable("book.finalizeWarning"),
				panelX + 12, panelY + 100, panelWidth - 24, 0xFFB0B0B0, false);
	}
}
