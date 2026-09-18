package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.text.BookTemplate;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.TextSet;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The templates: the five that come with the mod and any page you have kept yourself.
 *
 * <p>The chosen one is shown as it will land on the page, on a strip exactly as wide as a page,
 * because the whole value of a template is the setting out and a list of names conveys none of it.
 */
public class TemplateScreen extends DialogScreen {
	private static final int ROW = 16;
	private static final int SHOWN = 8;

	private final PageEditor editor;

	/** What can be chosen: the built-in ones first, then whatever has been kept. */
	private record Choice(String name, Text label, List<Paragraph> page, boolean own) {
	}

	private final List<Choice> choices = new ArrayList<>();
	private int chosen;
	private TextFieldWidget name;
	@Nullable
	private ButtonWidget deleteButton;

	/**
	 * Whether the list is showing whole pages or pieces of one.
	 *
	 * <p>Both in one window on purpose. They are the same idea at two sizes – something set out
	 * already, kept under a name – and giving the second one its own button in the book would have
	 * been a second button for nothing.
	 */
	private boolean showingSets;

	public TemplateScreen(@Nullable Screen parent, PageEditor editor) {
		super(parent, Text.translatable("roleplayersquill.template.title"));
		this.editor = editor;
		this.panelWidth = 300;
		this.panelHeight = 44 + SHOWN * ROW + 10 + 26 + 26;
		gather();
	}

	private void gather() {
		choices.clear();
		if (showingSets) {
			for (TextSet set : TextSet.values()) {
				choices.add(new Choice(set.name(), set.label(), set.paragraphs(), false));
			}
			for (Map.Entry<String, List<Paragraph>> kept : BookIO.sets().entrySet()) {
				choices.add(new Choice(kept.getKey(), Text.literal(kept.getKey()), kept.getValue(), true));
			}
		} else {
			for (BookTemplate template : BookTemplate.values()) {
				choices.add(new Choice(template.name(), template.label(), template.page(), false));
			}
			for (Map.Entry<String, List<Paragraph>> kept : BookIO.templates().entrySet()) {
				choices.add(new Choice(kept.getKey(), Text.literal(kept.getKey()), kept.getValue(), true));
			}
		}
		chosen = Math.min(chosen, Math.max(0, choices.size() - 1));
	}

	@Override
	protected void init() {
		super.init();

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.template.tabPages"),
						b -> switchTo(false))
				.dimensions(panelX + 12, panelY + 22, 138, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.template.tabSets"),
						b -> switchTo(true))
				.dimensions(panelX + 152, panelY + 22, 136, 18).build());

		int y = panelY + 44;
		for (int i = 0; i < Math.min(choices.size(), SHOWN); i++) {
			int index = i;
			addDrawableChild(ButtonWidget.builder(choices.get(i).label(), b -> chosen = index)
					.dimensions(panelX + 12, y, 126, 14).build());
			y += ROW;
		}

		int bottom = panelY + panelHeight - 52;
		name = new TextFieldWidget(textRenderer, panelX + 12, bottom, 150, 18,
				Text.translatable("roleplayersquill.template.name"));
		name.setMaxLength(32);
		name.setPlaceholder(Text.translatable("roleplayersquill.template.name").formatted(Formatting.DARK_GRAY));
		addDrawableChild(name);

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.template.save"), b -> {
			String given = name.getText().isBlank()
					? Text.translatable("roleplayersquill.template.name").getString()
					: name.getText();
			if (showingSets) {
				// A set is kept from what is selected, or from the paragraph the caret is in when
				// nothing is: keeping the whole page would be keeping a template.
				BookIO.saveSet(given, editor.paragraphsForSet());
			} else {
				BookIO.saveTemplate(given, editor.currentPage());
			}
			gather();
			clearAndInit();
		}).dimensions(panelX + 168, bottom, 120, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.template.insert"), b -> {
			if (!choices.isEmpty()) {
				if (showingSets) {
					editor.insertSet(choices.get(chosen).page());
				} else {
					editor.insertPageAfter(choices.get(chosen).page());
				}
			}
			close();
		}).dimensions(panelX + 12, panelY + panelHeight - 26, 100, 20).build());

		// Kept so that whether it can be pressed is decided every frame. Set once here, it answered
		// for whichever template was chosen when the window was last built – so deleting worked on
		// the wrong ones and not on the right ones.
		deleteButton = addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.template.delete"), b -> {
			if (!choices.isEmpty() && choices.get(chosen).own()) {
				if (showingSets) {
					BookIO.deleteSet(choices.get(chosen).name());
				} else {
					BookIO.deleteTemplate(choices.get(chosen).name());
				}
				gather();
				clearAndInit();
			}
		}).dimensions(panelX + 118, panelY + panelHeight - 26, 80, 20).build());

		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
				.dimensions(panelX + 204, panelY + panelHeight - 26, 84, 20).build());
	}

	private void switchTo(boolean sets) {
		if (showingSets == sets) {
			return;
		}
		showingSets = sets;
		chosen = 0;
		gather();
		clearAndInit();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Worked out here rather than when the window was built: what is chosen changes without the
		// window being rebuilt, and the answer changes with it.
		if (deleteButton != null) {
			deleteButton.active = !choices.isEmpty() && choices.get(chosen).own();
		}
		super.render(context, mouseX, mouseY, delta);

		int y = panelY + 44;
		for (int i = 0; i < Math.min(choices.size(), SHOWN); i++) {
			if (i == chosen) {
				context.fill(panelX + 6, y + 2, panelX + 10, y + 12, 0xFFE8D8A0);
			}
			y += ROW;
		}

		if (choices.isEmpty()) {
			return;
		}
		int previewX = panelX + 152;
		int previewY = panelY + 44;
		List<Paragraph> page = choices.get(chosen).page();
		List<Layout.LaidLine> lines = Layout.lay(page, editor.layoutOptions());
		int rows = Math.min(lines.size(), SHOWN);
		context.fill(previewX - 3, previewY - 3, previewX + (int) Layout.PAGE_WIDTH + 3,
				previewY + rows * Layout.LINE_HEIGHT + 3, 0xFFE9DBBF);
		context.drawBorder(previewX - 3, previewY - 3, (int) Layout.PAGE_WIDTH + 6,
				rows * Layout.LINE_HEIGHT + 6, 0xFF6B5A3E);
		for (int i = 0; i < rows; i++) {
			BookPreview.drawLine(context, textRenderer, lines.get(i), page, previewX,
					previewY + i * Layout.LINE_HEIGHT);
		}

		context.drawText(textRenderer,
				Text.translatable("roleplayersquill.template.hint").formatted(Formatting.DARK_GRAY),
				panelX + 12, panelY + panelHeight - 66, 0xFFFFFFFF, false);
	}
}
