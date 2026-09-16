package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What was cut or copied, kept twice.
 *
 * <p>The system clipboard gets plain characters, so that a passage can be pasted into a chat, a
 * text editor, or another game. Alongside it the styling is kept here, and a paste uses it only
 * when the system clipboard still holds the same characters – which is how a copy inside the editor
 * keeps its colours and a copy from a web page does not arrive claiming to have any.
 */
public final class QuillClipboard {
	private static List<Paragraph> styled = List.of();
	private static String plain = "";

	private static List<Paragraph> page = List.of();

	private QuillClipboard() {
	}

	public static void put(List<Paragraph> paragraphs, String asPlainText) {
		styled = QuillDocument.copyPage(paragraphs);
		plain = asPlainText;
	}

	/** The styled version, if the system clipboard still holds what we put there. */
	@Nullable
	public static List<Paragraph> matching(String systemText) {
		if (styled.isEmpty() || !plain.equals(systemText)) {
			return null;
		}
		return QuillDocument.copyPage(styled);
	}

	public static void putPage(List<Paragraph> paragraphs) {
		page = QuillDocument.copyPage(paragraphs);
	}

	public static boolean hasPage() {
		return !page.isEmpty();
	}

	public static List<Paragraph> takePage() {
		return QuillDocument.copyPage(page);
	}
}
