package com.glamardor.roleplayersquill.screen;

import net.minecraft.client.input.MouseButtonEvent;

import com.glamardor.roleplayersquill.text.BookTools;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
		super(parent, Component.translatable("roleplayersquill.contents.title"));
		this.view = view;
		this.headings = BookTools.headings(view.document().pages(), view.document().title());
		this.panelWidth = 300;
		this.panelHeight = Math.max(3, Math.min(VISIBLE, headings.size())) * ROW + 56;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		int listX = panelX + 12;
		int listY = panelY + 24;
		int hovered = rowAt(mouseX, mouseY);

		if (headings.isEmpty()) {
			context.centeredText(font,
					Component.translatable("roleplayersquill.reader.contents.none").withStyle(ChatFormatting.GRAY),
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
			Component label = Component.literal(font.plainSubstrByWidth(entry.text(), panelWidth - 60 - indent))
					.withStyle(entry.under() ? ChatFormatting.GRAY : ChatFormatting.WHITE);
			context.text(font, label, listX + 4 + indent, y + 4, 0xFFFFFFFF, false);
			context.text(font,
					Component.literal("p. " + (entry.page() + 1)).withStyle(ChatFormatting.DARK_GRAY),
					panelX + panelWidth - 44, y + 4, 0xFFFFFFFF, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		int button = Input.button(event);
		int index = rowAt(mouseX, mouseY);
		if (index >= 0) {
			view.setPage(headings.get(index).page());
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Mth.clamp(scroll - (int) Math.signum(vertical), 0,
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
