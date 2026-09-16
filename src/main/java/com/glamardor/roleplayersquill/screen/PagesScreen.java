package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The whole book at a glance: every page with its opening line, and the buttons to shuffle them.
 *
 * <p>Turning a hundred pages one at a time to find the one with the map on it is the sort of thing
 * an editor should make unnecessary.
 */
public class PagesScreen extends DialogScreen {
	private static final int ROW = 22;
	private static final int VISIBLE = 7;

	private final PageEditor editor;
	private int scroll;
	private int chosen;

	public PagesScreen(@Nullable Screen parent, PageEditor editor) {
		super(parent, Text.translatable("roleplayersquill.pages.title"));
		this.editor = editor;
		this.chosen = editor.page();
		this.panelWidth = 300;
		this.panelHeight = VISIBLE * ROW + 84;
	}

	@Override
	protected void init() {
		super.init();
		scroll = MathHelper.clamp(chosen - VISIBLE / 2, 0, Math.max(0, editor.document().pageCount() - VISIBLE));

		int y = panelY + panelHeight - 52;
		addDrawableChild(ButtonWidget.builder(Text.literal("▲"), button -> move(-1))
				.dimensions(panelX + 12, y, 26, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("▼"), button -> move(1))
				.dimensions(panelX + 42, y, 26, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.pages.duplicate"), button -> {
			editor.setPage(chosen);
			editor.duplicatePage();
			chosen = editor.page();
			clearAndInit();
		}).dimensions(panelX + 72, y, 96, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.pages.delete"), button -> {
			editor.setPage(chosen);
			editor.removePage();
			chosen = editor.page();
			clearAndInit();
		}).dimensions(panelX + 172, y, 116, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.pages.go"), button -> {
			editor.setPage(chosen);
			close();
		}).dimensions(panelX + 12, panelY + panelHeight - 28, 140, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(panelX + 158, panelY + panelHeight - 28, 130, 20).build());
	}

	private void move(int delta) {
		int to = chosen + delta;
		if (to < 0 || to >= editor.document().pageCount()) {
			return;
		}
		editor.document().mark();
		editor.document().movePage(chosen, to);
		chosen = to;
		editor.touch();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int index = rowAt(mouseX, mouseY);
		if (index >= 0) {
			chosen = index;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = MathHelper.clamp(scroll - (int) Math.signum(vertical), 0,
				Math.max(0, editor.document().pageCount() - VISIBLE));
		return true;
	}

	private int rowAt(double mouseX, double mouseY) {
		int listX = panelX + 12;
		int listY = panelY + 24;
		if (mouseX < listX || mouseX > panelX + panelWidth - 12) {
			return -1;
		}
		// Math.floor, not a cast: a cast rounds towards zero and a click above the list would land
		// on its first row.
		int row = (int) Math.floor((mouseY - listY) / (double) ROW);
		if (row < 0 || row >= VISIBLE) {
			return -1;
		}
		int index = scroll + row;
		return index < editor.document().pageCount() ? index : -1;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int listX = panelX + 12;
		int listY = panelY + 24;
		int hovered = rowAt(mouseX, mouseY);

		for (int row = 0; row < VISIBLE; row++) {
			int index = scroll + row;
			if (index >= editor.document().pageCount()) {
				break;
			}
			int y = listY + row * ROW;
			int background = index == chosen ? 0x803C6390 : index == hovered ? 0x40FFFFFF : 0x30000000;
			context.fill(listX, y, panelX + panelWidth - 12, y + ROW - 2, background);

			context.drawText(textRenderer, Text.literal(String.valueOf(index + 1)).formatted(Formatting.GOLD),
					listX + 4, y + 3, 0xFFFFFFFF, false);
			context.drawText(textRenderer, summary(editor.document().page(index)),
					listX + 26, y + 3, 0xFFD0D0D0, false);
			context.drawText(textRenderer, Text.translatable("roleplayersquill.pages.chars",
							LegacyCodec.strip(firstLine(editor.document().page(index))).length())
					.formatted(Formatting.DARK_GRAY), listX + 26, y + 12, 0xFFFFFFFF, false);
		}

		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("roleplayersquill.pages.count",
						editor.document().pageCount(), QuillDocument.MAX_PAGES).formatted(Formatting.GRAY),
				width / 2, panelY + panelHeight - 66, 0xFFFFFFFF);
	}

	private Text summary(List<Paragraph> page) {
		String line = firstLine(page);
		if (line.isBlank()) {
			return Text.translatable("roleplayersquill.pages.empty").formatted(Formatting.DARK_GRAY);
		}
		return Text.literal(textRenderer.trimToWidth(line, panelWidth - 60));
	}

	private static String firstLine(List<Paragraph> page) {
		StringBuilder out = new StringBuilder();
		for (Paragraph paragraph : page) {
			if (paragraph.isEmpty()) {
				continue;
			}
			out.append(paragraph.text()).append(' ');
			if (out.length() > 80) {
				break;
			}
		}
		return out.toString().strip();
	}
}
