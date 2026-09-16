package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillStyle;
import com.glamardor.roleplayersquill.text.TableBuilder;
import com.glamardor.roleplayersquill.text.Widths;
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
import java.util.function.Consumer;

/**
 * The table builder: a grid you type into.
 *
 * <p>It used to be a text box where cells were separated by bars, which is a fine way to describe a
 * table and a poor way to build one – you cannot see the columns while you are filling them, and a
 * bar in the wrong place silently makes a different table. Here the cells are cells, the rows and
 * columns are added and taken away with buttons, and the page underneath shows what the book will
 * get at the width the book will give it.
 */
public class TableScreen extends DialogScreen {
	private static final int MAX_COLUMNS = 5;
	private static final int MAX_ROWS = 10;
	private static final int CELL_HEIGHT = 18;

	private final Consumer<List<Paragraph>> whenBuilt;

	/** The table itself. Rebuilt into widgets whenever its shape changes. */
	private final List<List<String>> cells = new ArrayList<>();

	private TableBuilder.Style style = TableBuilder.Style.RULES;
	private TableBuilder.Columns columns = TableBuilder.Columns.AUTO;
	private TableBuilder.Heading heading = TableBuilder.Heading.BOLD;

	private List<Paragraph> preview = List.of();
	private boolean tooWide;

	private int gridY;
	private int previewY;

	public TableScreen(@Nullable Screen parent, Consumer<List<Paragraph>> whenBuilt) {
		super(parent, Text.translatable("roleplayersquill.table.title"));
		this.whenBuilt = whenBuilt;
		this.panelWidth = 330;
		fill(3, 2);
		cells.get(0).set(0, Text.translatable("roleplayersquill.table.cell.item").getString());
		cells.get(0).set(1, Text.translatable("roleplayersquill.table.cell.price").getString());
	}

	/** Makes the table this many rows by this many columns, keeping whatever was already typed. */
	private void fill(int rows, int columnCount) {
		while (cells.size() > rows) {
			cells.remove(cells.size() - 1);
		}
		while (cells.size() < rows) {
			cells.add(new ArrayList<>());
		}
		for (List<String> row : cells) {
			while (row.size() > columnCount) {
				row.remove(row.size() - 1);
			}
			while (row.size() < columnCount) {
				row.add("");
			}
		}
	}

	private int rowCount() {
		return cells.size();
	}

	private int columnCount() {
		return cells.isEmpty() ? 0 : cells.get(0).size();
	}

	@Override
	protected void init() {
		rebuild();
		int previewRows = Math.min(preview.size(), MAX_ROWS + 2);
		this.panelHeight = 46 + rowCount() * CELL_HEIGHT + 34 + previewRows * Layout.LINE_HEIGHT + 14 + 30;
		super.init();
		// Nudged down off the editor's own row of buttons, which the panel used to sit on top of.
		panelY = Math.min(panelY + 18, Math.max(2, height - panelHeight - 2));

		int y = panelY + 20;
		addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> resize(0, -1))
				.dimensions(panelX + 74, y, 14, 14).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> resize(0, 1))
				.dimensions(panelX + 106, y, 14, 14).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> resize(-1, 0))
				.dimensions(panelX + 196, y, 14, 14).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> resize(1, 0))
				.dimensions(panelX + 228, y, 14, 14).build());

		gridY = panelY + 40;
		int cellWidth = (panelWidth - 20) / Math.max(1, columnCount());
		for (int r = 0; r < rowCount(); r++) {
			for (int c = 0; c < columnCount(); c++) {
				int row = r;
				int column = c;
				TextFieldWidget field = new TextFieldWidget(textRenderer,
						panelX + 10 + c * cellWidth, gridY + r * CELL_HEIGHT, cellWidth - 3, 16,
						Text.translatable("roleplayersquill.table.cell"));
				field.setMaxLength(64);
				field.setText(cells.get(r).get(c));
				field.setChangedListener(value -> {
					cells.get(row).set(column, value);
					rebuild();
				});
				addDrawableChild(field);
			}
		}

		int controls = gridY + rowCount() * CELL_HEIGHT + 4;
		addDrawableChild(ButtonWidget.builder(style.label(), b -> {
			style = style.next();
			b.setMessage(style.label());
			rebuild();
		}).dimensions(panelX + 10, controls, 102, 18).build());
		addDrawableChild(ButtonWidget.builder(columns.label(), b -> {
			columns = columns.next();
			b.setMessage(columns.label());
			rebuild();
		}).dimensions(panelX + 116, controls, 102, 18).build());
		addDrawableChild(ButtonWidget.builder(heading.label(), b -> {
			heading = heading.next();
			b.setMessage(heading.label());
			rebuild();
		}).dimensions(panelX + 222, controls, 98, 18).build());

		// Below the buttons with room for the caption, which used to be drawn across them.
		previewY = controls + 34;

		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.table.insert"), b -> {
			whenBuilt.accept(preview);
			close();
		}).dimensions(panelX + 10, panelY + panelHeight - 26, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
				.dimensions(panelX + 170, panelY + panelHeight - 26, 150, 20).build());
	}

	private void resize(int columnDelta, int rowDelta) {
		int rows = Math.max(1, Math.min(MAX_ROWS, rowCount() + rowDelta));
		int cols = Math.max(1, Math.min(MAX_COLUMNS, columnCount() + columnDelta));
		fill(rows, cols);
		clearAndInit();
	}

	/** Rows with nothing in them at all are left out: an empty last row is a row not yet used. */
	private List<List<String>> filled() {
		List<List<String>> out = new ArrayList<>();
		for (List<String> row : cells) {
			boolean any = false;
			for (String cell : row) {
				any |= !cell.isBlank();
			}
			if (any) {
				out.add(new ArrayList<>(row));
			}
		}
		return out;
	}

	private void rebuild() {
		List<List<String>> rows = filled();
		preview = TableBuilder.build(rows, style, columns, heading);
		tooWide = !TableBuilder.fits(rows, style, heading);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		context.drawText(textRenderer, Text.translatable("roleplayersquill.table.rows"),
				panelX + 10, panelY + 23, 0xFFB0B0B0, false);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(String.valueOf(rowCount())),
				panelX + 97, panelY + 23, 0xFFFFFFFF);
		context.drawText(textRenderer, Text.translatable("roleplayersquill.table.columns"),
				panelX + 132, panelY + 23, 0xFFB0B0B0, false);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(String.valueOf(columnCount())),
				panelX + 219, panelY + 23, 0xFFFFFFFF);

		if (heading.present() && rowCount() > 0) {
			// A tint behind the first row, so it is obvious which one becomes the heading.
			int cellWidth = (panelWidth - 20) / Math.max(1, columnCount());
			context.fill(panelX + 8, gridY - 2, panelX + 10 + columnCount() * cellWidth,
					gridY + CELL_HEIGHT - 2, 0x30E8D8A0);
		}

		context.drawText(textRenderer, tooWide
						? Text.translatable("roleplayersquill.table.too_wide").formatted(Formatting.GOLD)
						: Text.translatable("roleplayersquill.table.preview").formatted(Formatting.DARK_GRAY),
				panelX + 10, previewY - 11, 0xFFFFFFFF, false);

		// The preview sits on a strip exactly 114 pixels wide, which is the page: a column that fits
		// here fits there.
		int previewX = panelX + (panelWidth - (int) Layout.PAGE_WIDTH) / 2;
		int rows = Math.min(preview.size(), MAX_ROWS + 2);
		context.fill(previewX - 3, previewY - 3, previewX + (int) Layout.PAGE_WIDTH + 3,
				previewY + rows * Layout.LINE_HEIGHT + 3, 0xFFE9DBBF);
		context.drawBorder(previewX - 3, previewY - 3, (int) Layout.PAGE_WIDTH + 6,
				rows * Layout.LINE_HEIGHT + 6, 0xFF6B5A3E);

		for (int i = 0; i < rows; i++) {
			Paragraph paragraph = preview.get(i);
			float x = previewX;
			int y = previewY + i * Layout.LINE_HEIGHT;
			StringBuilder run = new StringBuilder();
			QuillStyle runStyle = null;
			for (int c = 0; c < paragraph.length(); c++) {
				QuillStyle cellStyle = paragraph.styleAt(c);
				if (runStyle != null && !runStyle.equals(cellStyle)) {
					x = draw(context, run, runStyle, x, y);
				}
				runStyle = cellStyle;
				run.append(paragraph.charAt(c));
			}
			draw(context, run, runStyle, x, y);
		}
	}

	private float draw(DrawContext context, StringBuilder run, @Nullable QuillStyle style, float x, int y) {
		if (run.isEmpty()) {
			return x;
		}
		QuillStyle applied = style == null ? QuillStyle.PLAIN : style;
		String text = run.toString();
		context.drawText(textRenderer, Text.literal(text).setStyle(applied.toVanilla(0)),
				(int) x, y, 0xFF000000, false);
		run.setLength(0);
		return x + Widths.widthOf(text, applied.bold());
	}
}
