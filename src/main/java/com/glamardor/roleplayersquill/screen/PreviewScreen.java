package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The book as a reader will see it: the vanilla reader, given the very pages that are about to be
 * sent.
 *
 * <p>Not a lookalike drawn by this mod. Using the game's own screen is the point – if a line wraps
 * differently here than it does in the editor, the editor is wrong, and the only way to find that
 * out is to let the game lay it out itself.
 */
public class PreviewScreen extends BookScreen {
	@Nullable
	private final Screen parent;

	public PreviewScreen(@Nullable Screen parent, PageEditor editor) {
		super(new BookScreen.Contents(pagesFor(editor)));
		this.parent = parent;
	}

	/**
	 * Previewed the way it will be written: as components when the book can be sent that way, and as
	 * the strings the server would turn into components when it cannot.
	 */
	private static List<Text> pagesFor(PageEditor editor) {
		if (editor.needsRich() && BookSender.canWriteRich()) {
			return editor.encodeRichPages();
		}
		return editor.encodePages().stream().map(page -> (Text) Text.literal(page)).toList();
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
