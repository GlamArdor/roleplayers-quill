package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.BookTools;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The headings of a book, with a way to jump to any of them.
 *
 * <p>A book remembers nothing about a heading beyond what it looks like – bold and centred – so
 * this is the same test {@link BookTools#contentsFor} uses to build a contents page out of a book,
 * pointed at a click instead of at paper. It works on any book, this mod's own or somebody else's,
 * because a heading looks the same either way.
 */
public class ContentsScreen extends DialogScreen {
	private static final int ROW = 20;
	private static final int VISIBLE = 8;

	private final BookView view;
	private final List<BookTools.Heading> headings;
	private int scroll;

	public ContentsScreen(@Nullable Screen parent, BookView view) {
		super(parent, Text.translatable("roleplayersquill.contents.title"));
		this.view = view;
		this.headings = BookTools.headings(view.document().pages(), view.document().title());
		this.panelWidth = 300;
		this.panelHeight = Math.max(3, Math.min(VISIBLE, headings.size())) * ROW + 56;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int listX = panelX + 12;
		int listY = panelY + 24;
		int hovered = rowAt(mouseX, mouseY);

		if (headings.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("roleplayersquill.reader.contents.none").formatted(Formatting.GRAY),
					width / 2, listY + 8, 0xFFFFFFFF);
			return;
		}

		for (int row = 0; row < VISIBLE; row++) {
			int index = scroll + row;
			if (index >= headings.size()) {
				break;
			}
			BookTools.Heading entry = headings.get(index);
			int y = listY + row * ROW;
			context.fill(listX, y, panelX + panelWidth - 12, y + ROW - 2, index == hovered ? 0x40FFFFFF : 0x20000000);

			int indent = entry.under() ? 14 : 0;
			Text label = Text.literal(textRenderer.trimToWidth(entry.text(), panelWidth - 60 - indent))
					.formatted(entry.under() ? Formatting.GRAY : Formatting.WHITE);
			context.drawText(textRenderer, label, listX + 4 + indent, y + 4, 0xFFFFFFFF, false);
			context.drawText(textRenderer,
					Text.literal("p. " + (entry.page() + 1)).formatted(Formatting.DARK_GRAY),
					panelX + panelWidth - 44, y + 4, 0xFFFFFFFF, false);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int index = rowAt(mouseX, mouseY);
		if (index >= 0) {
			view.setPage(headings.get(index).page());
			close();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = MathHelper.clamp(scroll - (int) Math.signum(vertical), 0,
				Math.max(0, headings.size() - VISIBLE));
		return true;
	}

	private int rowAt(double mouseX, double mouseY) {
		int listX = panelX + 12;
		int listY = panelY + 24;
		if (mouseX < listX || mouseX > panelX + panelWidth - 12) {
			return -1;
		}
		int row = (int) Math.floor((mouseY - listY) / (double) ROW);
		if (row < 0 || row >= VISIBLE) {
			return -1;
		}
		int index = scroll + row;
		return index < headings.size() ? index : -1;
	}
}
