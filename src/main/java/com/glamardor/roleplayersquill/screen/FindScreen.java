package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.BookSearch;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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

	private TextFieldWidget needle;
	@Nullable
	private TextFieldWidget replacement;
	private CheckboxWidget matchCase;

	/** What was typed into the replacement box, kept while the box itself is folded away. */
	private String replaceWith = "";
	private boolean replacing;

	@Nullable
	private Text notice;

	/** Where the last match was, so that "next" carries on rather than starting over. */
	private BookSearch.Hit at = new BookSearch.Hit(0, 0, 0, 0);

	/** The book behind, when there is one, so this can stand under it rather than over it. */
	@Nullable
	private final QuillEditScreen book;

	public FindScreen(@Nullable Screen parent, PageEditor editor) {
		super(parent, Text.translatable("roleplayersquill.find.title"));
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

		String was = needle == null ? "" : needle.getText();
		needle = new TextFieldWidget(textRenderer, panelX + 12, panelY + 22, panelWidth - 24, 18,
				Text.translatable("roleplayersquill.find.needle"));
		needle.setMaxLength(120);
		needle.setPlaceholder(Text.translatable("roleplayersquill.find.needle").formatted(Formatting.DARK_GRAY));
		needle.setText(was);
		addDrawableChild(needle);
		setInitialFocus(needle);

		boolean wasCase = matchCase != null && matchCase.isChecked();
		matchCase = CheckboxWidget.builder(Text.translatable("roleplayersquill.find.case"), textRenderer)
				.pos(panelX + 12, panelY + 46)
				.checked(wasCase)
				.build();
		addDrawableChild(matchCase);

		int noticeY = panelY + 68;
		if (replacing) {
			replacement = new TextFieldWidget(textRenderer, panelX + 12, panelY + 68, panelWidth - 24, 18,
					Text.translatable("roleplayersquill.find.replacement"));
			replacement.setMaxLength(120);
			replacement.setPlaceholder(
					Text.translatable("roleplayersquill.find.replacement").formatted(Formatting.DARK_GRAY));
			replacement.setText(replaceWith);
			replacement.setChangedListener(value -> replaceWith = value);
			addDrawableChild(replacement);
			noticeY = panelY + 92;
		} else {
			replacement = null;
		}

		this.noticeY = noticeY;
		int y = noticeY + 12;
		if (replacing) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.find.next"), b -> findNext())
					.dimensions(panelX + 12, y, 88, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.find.replace"), b -> replaceOne())
					.dimensions(panelX + 106, y, 88, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.find.replaceAll"), b -> replaceAll())
					.dimensions(panelX + 200, y, 88, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.find.fold"), b -> fold(false))
					.dimensions(panelX + 12, y + 24, 130, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.symbols.close"), b -> close())
					.dimensions(panelX + 158, y + 24, 130, 20).build());
		} else {
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.find.next"), b -> findNext())
					.dimensions(panelX + 12, y, 100, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.find.unfold"), b -> fold(true))
					.dimensions(panelX + 118, y, 100, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.symbols.close"), b -> close())
					.dimensions(panelX + 224, y, 64, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.find.library"),
							b -> client.setScreen(new LibraryScreen(this, needle.getText())))
					.dimensions(panelX + 12, y + 24, 276, 20).build());
		}
	}

	private int noticeY;

	private void fold(boolean open) {
		replacing = open;
		clearAndInit();
	}

	/** Enter is find next, which is what it does in every other search box there has ever been. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			findNext();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void findNext() {
		String what = needle.getText();
		if (what.isEmpty()) {
			return;
		}
		BookSearch.Hit found = BookSearch.next(editor.document().pages(), what, matchCase.isChecked(), at);
		if (found == null) {
			notice = Text.translatable("roleplayersquill.find.none").formatted(Formatting.GRAY);
			return;
		}
		at = found;
		show(found);
		notice = Text.translatable("roleplayersquill.find.at", found.page() + 1).formatted(Formatting.GRAY);
	}

	/** Takes the editor to the match and selects it, so the page behind this window shows it. */
	private void show(BookSearch.Hit found) {
		editor.setPage(found.page());
		editor.setCaret(found.paragraph(), found.from(), false);
		editor.setCaret(found.paragraph(), found.to(), true);
	}

	private void replaceOne() {
		String what = needle.getText();
		if (what.isEmpty()) {
			return;
		}
		BookSearch.Hit found = BookSearch.next(editor.document().pages(), what, matchCase.isChecked(), at);
		if (found == null) {
			notice = Text.translatable("roleplayersquill.find.none").formatted(Formatting.GRAY);
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
		notice = Text.translatable("roleplayersquill.find.replaced", 1).formatted(Formatting.GRAY);
	}

	private void replaceAll() {
		String what = needle.getText();
		if (what.isEmpty()) {
			return;
		}
		editor.document().mark();
		int done = BookSearch.replaceAll(editor.document().pages(), what, replaceWith, matchCase.isChecked());
		editor.touch();
		at = new BookSearch.Hit(0, 0, 0, 0);
		notice = done == 0
				? Text.translatable("roleplayersquill.find.none").formatted(Formatting.GRAY)
				: Text.translatable("roleplayersquill.find.replaced", done).formatted(Formatting.GRAY);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		if (notice != null) {
			context.drawText(textRenderer, notice, panelX + 12, noticeY, 0xFFFFFFFF, false);
		}
	}
}
