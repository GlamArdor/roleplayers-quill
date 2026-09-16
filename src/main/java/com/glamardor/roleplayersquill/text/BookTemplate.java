package com.glamardor.roleplayersquill.text;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Books that start half-written: a decree, a letter, a notice, a contract, a diary.
 *
 * <p>Not to save typing – the words in them are two lines each. To save the setting out: what is a
 * heading, what is a caption, where the signature goes. Somebody writing their first decree on a
 * roleplay server does not know what a decree is supposed to look like, and a blank page does not
 * tell them.
 */
public enum BookTemplate {
	DECREE(5),
	LETTER(5),
	NOTICE(4),
	CONTRACT(6),
	DIARY(4);

	/** How many lines the template has, each of them a translated string. */
	private final int lines;

	BookTemplate(int lines) {
		this.lines = lines;
	}

	public Text label() {
		return Text.translatable(key() + ".name");
	}

	private String key() {
		return "roleplayersquill.template." + name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The template as a page.
	 *
	 * <p>Each line carries its own paragraph style, named in the language file beside the line
	 * itself: a line reading {@code heading|Указ} is a heading. Keeping the two together means a
	 * translation can move a signature or drop a caption without anything here knowing.
	 */
	public List<Paragraph> page() {
		List<Paragraph> out = new ArrayList<>();
		for (int i = 1; i <= lines; i++) {
			// The date is filled in as the page is made, in the server's own reckoning. A template
			// with a blank where the date goes is a template everybody fills in wrongly.
			String line = Text.translatable(key() + ".line" + i).getString()
					.replace("{date_of}", ServerDate.todayAfterPreposition())
					.replace("{date}", ServerDate.today());
			ParagraphStyle style = ParagraphStyle.NORMAL;
			int bar = line.indexOf('|');
			if (bar > 0) {
				try {
					style = ParagraphStyle.valueOf(line.substring(0, bar).toUpperCase(Locale.ROOT));
					line = line.substring(bar + 1);
				} catch (IllegalArgumentException error) {
					// Not a style after all; the bar is part of the text.
				}
			}
			Paragraph paragraph = line.isEmpty()
					? new Paragraph()
					: new Paragraph(line, QuillStyle.PLAIN);
			style.applyTo(paragraph);
			out.add(paragraph);
		}
		return out;
	}
}
