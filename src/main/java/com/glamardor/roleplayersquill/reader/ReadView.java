package com.glamardor.roleplayersquill.reader;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.screen.BookView;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A signed book, on the far side of {@link BookView} – so that {@link
 * com.glamardor.roleplayersquill.screen.FindBar}, {@link com.glamardor.roleplayersquill.screen.PagesScreen}
 * and {@link com.glamardor.roleplayersquill.screen.ExportScreen} work over a book being read
 * exactly as they work over one still being written.
 *
 * <p>Turning a page goes through the host rather than a field of its own, because on a lectern a
 * page turn has to reach the server – everyone else looking at the same lectern is watching too.
 * There is otherwise nothing here that changes anything: {@link #editable()} is false, and the
 * document {@link SignedBook} builds is thrown away the moment the screen closes.
 */
public final class ReadView implements BookView {
	private final ReadHost host;

	@Nullable
	private QuillDocument document;

	/** The paragraph and start a search hit landed on, waiting for its matching end. */
	private int pendingParagraph = -1;
	private int pendingFrom;

	/** The text of the last match, which occurrence of it this is, and on which page it was found. */
	@Nullable
	private String matchNeedle;
	private int matchOrdinalOnPage;
	private int matchPage = -1;

	public ReadView(ReadHost host) {
		this.host = host;
	}

	@Override
	public QuillDocument document() {
		if (document == null) {
			SignedBook.Info info = SignedBook.infoFor(host.asScreen());
			document = SignedBook.documentFor(host.contents(), info == null ? "" : info.title());
			// Everything about the book that is not written in it. Nothing here uses the author or
			// the lore while the book is being read; they are carried so that the copy put on the
			// shelf is a copy of the book rather than of its text.
			if (info != null) {
				document.setAuthor(info.author());
				document.setLore(info.lore());
			}
		}
		return document;
	}

	@Override
	public int page() {
		return host.pageIndex();
	}

	@Override
	public void setPage(int index) {
		host.jumpTo(index);
	}

	@Override
	public List<String> encodePages() {
		List<String> out = new ArrayList<>(document().pageCount());
		for (List<Paragraph> page : document().pages()) {
			out.add(LegacyCodec.encode(page, Layout.lay(page, QuillConfig.get().layoutOptions())));
		}
		return out;
	}

	/**
	 * Where {@code FindBar} moved to. There is no caret in a book being read, so what is kept
	 * instead is the matched text and which occurrence of it on the page this was – enough for
	 * {@link ReadSelection} to find the very same run among the lines the game actually wrapped,
	 * without the two ever having to agree on a character offset neither of them shares.
	 */
	@Override
	public void setCaret(int paragraphIndex, int index, boolean extend) {
		if (!extend) {
			pendingParagraph = paragraphIndex;
			pendingFrom = index;
			return;
		}
		if (paragraphIndex != pendingParagraph) {
			return;
		}
		List<Paragraph> page = document().page(page());
		if (pendingParagraph < 0 || pendingParagraph >= page.size()) {
			return;
		}
		String text = page.get(pendingParagraph).text();
		int from = Math.max(0, Math.min(pendingFrom, text.length()));
		int to = Math.max(from, Math.min(index, text.length()));
		matchNeedle = text.substring(from, to);
		matchOrdinalOnPage = ordinalOnPage(page, pendingParagraph, from);
		matchPage = page();
	}

	/** How many times {@link #matchNeedle} occurs at or before this point, counting from the top. */
	private int ordinalOnPage(List<Paragraph> page, int paragraphIndex, int matchStart) {
		if (matchNeedle == null || matchNeedle.isEmpty()) {
			return 0;
		}
		String needle = matchNeedle.toLowerCase(java.util.Locale.ROOT);
		int ordinal = 0;
		for (int p = 0; p <= paragraphIndex && p < page.size(); p++) {
			String text = page.get(p).text().toLowerCase(java.util.Locale.ROOT);
			int limit = p == paragraphIndex ? matchStart + 1 : text.length();
			int at = text.indexOf(needle);
			while (at >= 0 && at < limit) {
				ordinal++;
				at = text.indexOf(needle, at + 1);
			}
		}
		return ordinal;
	}

	/** The text of the last match found on the current page, when there is one. */
	@Nullable
	public String matchNeedle() {
		return matchNeedle;
	}

	/** Which occurrence of {@link #matchNeedle()} on its page this is, counting from one. */
	public int matchOrdinalOnPage() {
		return matchOrdinalOnPage;
	}

	/**
	 * Which page {@link #matchNeedle()} was found on – not necessarily the one showing now.
	 *
	 * <p>Turning the page some other way than through the find strip does not forget a match, it
	 * just stops it being drawn: come back to that page and it is still there, the same way a
	 * browser's find bar remembers what it found after the page scrolls past it.
	 */
	public int matchPage() {
		return matchPage;
	}

	/** Forgets the last match, once the strip that found it has closed. */
	public void clearMatch() {
		matchNeedle = null;
		matchPage = -1;
		pendingParagraph = -1;
	}
}
