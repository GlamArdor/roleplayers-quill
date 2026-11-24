package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.input.KeyEvent;

import com.glamardor.roleplayersquill.text.BookSearch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Find and replace, over the whole book.
 *
 * <p>Small to begin with, because finding is what it is nearly always opened for: a box, a switch
 * and a button. Replacing is a second half that unfolds underneath when it is asked for, and folds
 * away again – so that looking for a word is not conducted through a form for changing one.
 *
 * <p>Finding moves the editor to the page the match is on and selects it, so closing this leaves the
 * caret where the match was. That is the difference between a search that helps and one that only
 * tells you a word is somewhere in eighteen pages.
 */
public class FindScreen extends DialogScreen {
	private final PageEditor editor;

	private EditBox needle;
	@Nullable
	private EditBox replacement;
	private Checkbox matchCase;

	/** What was typed into the replacement box, kept while the box itself is folded away. */
	private String replaceWith = "";
	private boolean replacing;

	@Nullable
	private Component notice;

	/** Where the last match was, so that "next" carries on rather than starting over. */
	private BookSearch.Hit at = new BookSearch.Hit(0, 0, 0, 0);

	/** The book behind, when there is one, so this can stand under it rather than over it. */
	@Nullable
	private final QuillEditScreen book;

	public FindScreen(@Nullable Screen parent, PageEditor editor) {
		super(parent, Component.translatable("roleplayersquill.find.title"));
		this.book = parent instanceof QuillEditScreen screen ? screen : null;
		this.editor = editor;
		this.panelWidth = 300;
	}

	/**
	 * Under the book, not over it.
	 *
	 * <p>A window for finding things on a page has no business covering the page. It goes below the
	 * row with Sign and Done on it, and is pushed back up only if there is not the room.
	 */
	@Override
	protected int panelTop() {
		if (book == null) {
			return super.panelTop();
		}
		return Math.max(2, Math.min(book.belowButtons() + 4, height - panelHeight - 2));
	}

	@Override
	protected void init() {
		this.panelHeight = replacing ? 158 : 134;
		super.init();

		String was = needle == null ? "" : needle.getValue();
		needle = new EditBox(font, panelX + 12, panelY + 22, panelWidth - 24, 18,
				Component.translatable("roleplayersquill.find.needle"));
		needle.setMaxLength(120);
		needle.setHint(Component.translatable("roleplayersquill.find.needle").withStyle(ChatFormatting.DARK_GRAY));
		needle.setValue(was);
		addRenderableWidget(needle);
		setInitialFocus(needle);

		boolean wasCase = matchCase != null && matchCase.selected();
		matchCase = Checkbox.builder(Component.translatable("roleplayersquill.find.case"), font)
				.pos(panelX + 12, panelY + 46)
				.selected(wasCase)
				.build();
		addRenderableWidget(matchCase);

		int noticeY = panelY + 68;
		if (replacing) {
			replacement = new EditBox(font, panelX + 12, panelY + 68, panelWidth - 24, 18,
					Component.translatable("roleplayersquill.find.replacement"));
			replacement.setMaxLength(120);
			replacement.setHint(
					Component.translatable("roleplayersquill.find.replacement").withStyle(ChatFormatting.DARK_GRAY));
			replacement.setValue(replaceWith);
			replacement.setResponder(value -> replaceWith = value);
			addRenderableWidget(replacement);
			noticeY = panelY + 92;
		} else {
			replacement = null;
		}

		this.noticeY = noticeY;
		int y = noticeY + 12;
		if (replacing) {
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.find.next"), b -> findNext())
					.bounds(panelX + 12, y, 88, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.find.replace"), b -> replaceOne())
					.bounds(panelX + 106, y, 88, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.find.replaceAll"), b -> replaceAll())
					.bounds(panelX + 200, y, 88, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.find.fold"), b -> fold(false))
					.bounds(panelX + 12, y + 24, 130, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.symbols.close"), b -> onClose())
					.bounds(panelX + 158, y + 24, 130, 20).build());
		} else {
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.find.next"), b -> findNext())
					.bounds(panelX + 12, y, 100, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.find.unfold"), b -> fold(true))
					.bounds(panelX + 118, y, 100, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.symbols.close"), b -> onClose())
					.bounds(panelX + 224, y, 64, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("roleplayersquill.find.library"),
							b -> minecraft.gui.setScreen(new LibraryScreen(this, needle.getValue())))
					.bounds(panelX + 12, y + 24, 276, 20).build());
		}
	}

	private int noticeY;

	private void fold(boolean open) {
		replacing = open;
		rebuildWidgets();
	}

	/** Enter is find next, which is what it does in every other search box there has ever been. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key(), scanCode = event.keycode(), modifiers = event.modifiers();
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			findNext();
			return true;
		}
		return super.keyPressed(event);
	}

	private void findNext() {
		String what = needle.getValue();
		if (what.isEmpty()) {
			return;
		}
		BookSearch.Hit found = BookSearch.next(editor.document().pages(), what, matchCase.selected(), at);
		if (found == null) {
			notice = Component.translatable("roleplayersquill.find.none").withStyle(ChatFormatting.GRAY);
			return;
		}
		at = found;
		show(found);
		notice = Component.translatable("roleplayersquill.find.at", found.page() + 1).withStyle(ChatFormatting.GRAY);
	}

	/** Takes the editor to the match and selects it, so the page behind this window shows it. */
	private void show(BookSearch.Hit found) {
		editor.setPage(found.page());
		editor.setCaret(found.paragraph(), found.from(), false);
		editor.setCaret(found.paragraph(), found.to(), true);
	}

	private void replaceOne() {
		String what = needle.getValue();
		if (what.isEmpty()) {
			return;
		}
		BookSearch.Hit found = BookSearch.next(editor.document().pages(), what, matchCase.selected(), at);
		if (found == null) {
			notice = Component.translatable("roleplayersquill.find.none").withStyle(ChatFormatting.GRAY);
			return;
		}
		editor.document().mark();
		BookSearch.replace(editor.document().page(found.page()).get(found.paragraph()),
				found.from(), found.to(), replaceWith);
		editor.touch();
		// Carrying on from the end of what was written, so that replacing "а" with "аа" does not
		// find the second half of what it has just put in.
		at = new BookSearch.Hit(found.page(), found.paragraph(), found.from(),
				found.from() + replaceWith.length());
		show(at);
		notice = Component.translatable("roleplayersquill.find.replaced", 1).withStyle(ChatFormatting.GRAY);
	}

	private void replaceAll() {
		String what = needle.getValue();
		if (what.isEmpty()) {
			return;
		}
		editor.document().mark();
		int done = BookSearch.replaceAll(editor.document().pages(), what, replaceWith, matchCase.selected());
		editor.touch();
		at = new BookSearch.Hit(0, 0, 0, 0);
		notice = done == 0
				? Component.translatable("roleplayersquill.find.none").withStyle(ChatFormatting.GRAY)
				: Component.translatable("roleplayersquill.find.replaced", done).withStyle(ChatFormatting.GRAY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		if (notice != null) {
			context.text(font, notice, panelX + 12, noticeY, 0xFFFFFFFF, false);
		}
	}
}
