package com.glamardor.roleplayersquill.reader;

import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import com.glamardor.roleplayersquill.text.QuillStyle;
import com.glamardor.roleplayersquill.text.Widths;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.LecternScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a page being read is – in the shapes the rest of the editor already knows how to work with.
 *
 * <p>{@link com.glamardor.roleplayersquill.mixin.BookScreenContentsMixin} decorates the {@code Text}
 * a vanilla book renders with clickable addresses; this goes the rest of the way, back into a
 * {@link QuillDocument} of the kind {@link com.glamardor.roleplayersquill.screen.PageEditor} holds,
 * so that finding, contents, the page list and export all work on a signed book exactly as they
 * work on one still being written.
 *
 * <h2>How a component becomes a document</h2>
 *
 * <p>{@link LegacyCodec#decode} already reads a written page back into paragraphs, alignment and
 * lists included – it has to, since that is what happens every time a book saved by an older
 * version of this mod, or written by somebody else's, is opened again. A page read out of a
 * component rather than a string is flattened into the same {@code §}-coded text first, so the
 * one reader serves both. What a flattening throws away – an exact colour, a link, a tooltip – is
 * patched back on afterwards, run by run, onto the paragraphs {@code decode} produced: since every
 * paragraph's text is nothing more than a suffix of the line it came from (decode only ever trims
 * the leading blanks it read as an alignment), where that suffix begins in the flattened line is
 * arithmetic, not a search.
 */
public final class SignedBook {
	private SignedBook() {
	}

	/**
	 * The pages of a signed book, read back into a document exactly as if they had been typed.
	 *
	 * <p>Built straight onto {@link QuillDocument#pages()} rather than through {@code insertPage},
	 * which refuses a page past the hundred a book still being written is allowed to grow to – a
	 * book already holding a hundred is not growing, it is being read.
	 */
	public static QuillDocument documentFor(BookScreen.Contents contents, String title) {
		QuillDocument document = new QuillDocument();
		int count = contents.getPageCount();
		List<List<Paragraph>> pages = new ArrayList<>(Math.max(1, count));
		for (int i = 0; i < count; i++) {
			pages.add(pageOf(contents.getPage(i)));
		}
		if (pages.isEmpty()) {
			pages.add(QuillDocument.newPage());
		}
		document.pages().clear();
		document.pages().addAll(pages);
		if (!title.isBlank()) {
			document.setTitle(title);
		}
		return document;
	}

	/** One page of a signed book, read back into paragraphs with their exact styling restored. */
	public static List<Paragraph> pageOf(Text page) {
		StringBuilder clean = new StringBuilder();
		List<Style> styles = new ArrayList<>();
		page.visit((style, run) -> {
			for (int i = 0; i < run.length(); i++) {
				char c = run.charAt(i);
				if (c == LegacyCodec.SECTION) {
					continue;
				}
				clean.append(c == Widths.NOBREAK ? ' ' : c);
				styles.add(style);
			}
			return java.util.Optional.empty();
		}, Style.EMPTY);

		String[] segments = clean.toString().split("\n", -1);
		List<Paragraph> paragraphs = LegacyCodec.decode(clean.toString());
		// decode() produces exactly one paragraph per line of what it was given; segments is the
		// same split done the same way, so the two line up one for one.

		int offset = 0;
		for (int i = 0; i < segments.length && i < paragraphs.size(); i++) {
			String segment = segments[i];
			Paragraph paragraph = paragraphs.get(i);
			String text = paragraph.text();
			int cut = segment.length() - text.length();
			if (cut >= 0 && text.length() > 0) {
				int start = offset + cut;
				for (int j = 0; j < text.length(); j++) {
					int at = start + j;
					if (at < styles.size()) {
						QuillStyle exact = QuillStyle.from(styles.get(at));
						paragraph.restyle(j, j + 1, ignored -> exact);
					}
				}
			}
			offset += segment.length() + 1;
		}
		return paragraphs;
	}

	// ---- who wrote it, and how many times it has been copied -----------------------------------

	public record Info(String title, String author, int generation) {
		/** Whether this is a first-hand copy, or a copy of one. */
		public boolean isCopy() {
			return generation > 0;
		}

		/** Whether this copy can be copied again. */
		public boolean isTattered() {
			return generation >= 2;
		}
	}

	/**
	 * The title, the author and the generation of the book a screen is showing, when it can be
	 * found at all.
	 *
	 * <p>A lectern keeps the item it is displaying and hands it over on request. A book read out of
	 * a hand does not stay attached to the screen that opened for it – {@code BookScreen} only ever
	 * gets the pages – so {@link OpenBookContext} is where that item was set aside, at the moment
	 * {@code Contents.create} read it to build those very pages.
	 */
	@Nullable
	public static Info infoFor(BookScreen screen) {
		ItemStack stack = screen instanceof LecternScreen lectern
				? lectern.getScreenHandler().getBookItem()
				: OpenBookContext.peek();
		if (stack == null) {
			return null;
		}
		WrittenBookContentComponent content = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
		if (content == null) {
			return null;
		}
		return new Info(content.title().raw(), content.author(), content.generation());
	}
}
