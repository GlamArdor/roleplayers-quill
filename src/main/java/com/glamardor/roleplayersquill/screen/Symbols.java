package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.config.QuillConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The characters on offer, and how to find one.
 *
 * <p>Shared by the panel that docks beside the book and the window that opens over the chat, so
 * that the shelves and the search behave the same in both and there is one place to add to.
 *
 * <p>Two ways in. The shelves are hand-picked, because a wall of every character in Unicode is not
 * somewhere anybody finds anything. The search goes the other way and looks through eleven thousand
 * of them by their Unicode names, which is how a character nobody thought to shelve still turns up.
 */
public final class Symbols {
	public static final Map<String, String> SHELVES = new LinkedHashMap<>();

	static {
		SHELVES.put("punctuation",
				// No section sign here: see marks().
				"«»„“”‘’‚‛‹›–—―‒…·•‣∙°¶†‡¡¿‽※‼⁇⁈⁉⁂№@&*/\\|¦~^_‾¯´`¨˘˚˜ˆ′″‴‵‶‷⁀⁄⁊");
		SHELVES.put("brackets",
				"()[]{}⟨⟩⟪⟫⌈⌉⌊⌋⦃⦄⁅⁆「」『』【】〔〕〖〗〘〙〚〛《》〈〉‹›«»⸢⸣⸤⸥⟦⟧⟬⟭");
		SHELVES.put("arrows",
				"←↑→↓↔↕↖↗↘↙↚↛↜↝↞↟↠↡↢↣↤↥↦↧↩↪↫↬↭↮↰↱↲↳↴↵↶↷↺↻↼↽↾↿⇀⇁⇂⇃⇄⇅⇆⇇⇈⇉⇊"
						+ "⇐⇑⇒⇓⇔⇕⇖⇗⇘⇙⇚⇛⇜⇝⇠⇡⇢⇣⟵⟶⟷⟸⟹⟺➔➘➙➚➛➜➝➞➟➠➡➢➣➤➥➦➧➨➩➪➫➬➭➮➯"
						+ "➱➲➳➴➵➶➷➸➹➺➻➼➽➾⤴⤵⬅⬆⬇⬈⬉⬊⬋⬌⬍");
		SHELVES.put("maths",
				"±×÷≈≉≠≡≢≤≥≪≫∞√∛∜∑∏∫∬∮∂∆∇∈∉∋∌⊂⊃⊄⊅⊆⊇∪∩∅∀∃∄¬∧∨⊕⊖⊗⊘⊙⊥∥∠∡∢∴∵∝"
						+ "°′″‰‱½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞⅟↉№");
		SHELVES.put("frames",
				"─│┌┬┐├┼┤└┴┘╌╍╎╏═║╔╦╗╠╬╣╚╩╝╒╤╕╞╪╡╘╧╛╓╥╖╟╫╢╙╨╜╭╮╯╰╱╲╳"
						+ "▀▁▂▃▄▅▆▇█▉▊▋▌▍▎▏▐░▒▓▔▕▖▗▘▙▚▛▜▝▞▟");
		SHELVES.put("shapes",
				"■□▪▫▬▭▮▯▰▱▲△▴▵▶▷▸▹►▻▼▽▾▿◀◁◂◃◄◅◆◇◈◉◊○◌◍◎●◐◑◒◓◔◕◖◗◘◙◚◛◜◝◞◟◠◡◢◣◤◥◦◯"
						+ "⬛⬜⬝⬞⬟⬠⬡⬢⬣⬤⭐⭑⭒★☆✦✧✩✪✫✬✭✮✯✰❋❖✚✜✛✠✢✣✤✥");
		// The pickaxes, swords and anvils people actually want in a roleplay book. All of them live
		// in the plane the game's font covers; anything beyond it would draw as an empty box.
		SHELVES.put("items",
				"⚒⚔⚓⛏⛓⚙⚗⚖⚕⚘⚚⚛⚜⚑⚐⚠⚡⚰⚱⌛⏳⌚⌂⌨☎☏✁✂✃✄✆✇✈✉✎✏✐✑✒✓✔✕✖✗✘"
						+ "⛨⛉⛊⛋⛭⛮⛯⛰⛪⛩⛲⛳⛴⛵⛺⛽⚲⚴⚵⚶⚷⚸☕☘⌬⍟⏏⏩⏪⏫⏬");
		SHELVES.put("nature",
				"☀☁☂☃☄☼☽☾❀❁❂❃❄❅❆❇❈❉❊❋⚘☘⛰⛅⛆⛇☇☈☉☊☋☌☍♁♃♄♅♆♇⚕⚚⚸⯃⯄");
		SHELVES.put("signs",
				"☐☑☒☓☠☢☣☤☥☦☧☨☩☪☫☬☭☮☯☸♀♂⚢⚣⚤⚥⚦⚧⚨⚩♈♉♊♋♌♍♎♏♐♑♒♓⚖⚞⚟⚝⚜");
		SHELVES.put("games", "♠♡♢♣♤♥♦♧♔♕♖♗♘♙♚♛♜♝♞♟⚀⚁⚂⚃⚄⚅⛀⛁⛂⛃⚆⚇⚈⚉⛉⛊⛋");
		SHELVES.put("music", "♩♪♫♬♭♮♯⏏⏭⏮⏯⏸⏹⏺⏴⏵⏶⏷");
		SHELVES.put("currency",
				"₽$€£¥¢₴₸₹₺₼₾₿¤ƒ₡₢₣₤₥₦₧₨₩₪₫₭₮₯₰₱₲₳₵₶₷¤");
		SHELVES.put("greek",
				"αβγδεζηθικλμνξοπρστυφχψωΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩϐϑϒϕϖϰϱϲϳϴϵ");
		SHELVES.put("indices",
				"⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₒₓₔₕₖₗₘₙₚₛₜ");
		SHELVES.put("runes",
				"ᚠᚡᚢᚣᚤᚥᚦᚧᚨᚩᚪᚫᚬᚭᚮᚯᚰᚱᚲᚳᚴᚵᚶᚷᚸᚹᚺᚻᚼᚽᚾᚿᛀᛁᛂᛃᛄᛅᛆᛇᛈᛉᛊᛋᛌᛍᛎᛏᛐᛑᛒᛓᛔᛕᛖᛗ"
						+ "ᛘᛙᛚᛛᛜᛝᛞᛟᛠᛡᛢᛣᛤᛥᛦᛧᛨᛩᛪ");
		SHELVES.put("latin",
				"ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ"
						+ "ŒœŠšŸŽžĀāĂăĄąĆćĈĉĊċČčĎďĐđĒēĖėĘęĚěĜĝĞğĢģĤĥĨĩĪīĮįİıĴĵĶķĹĺĻļĽľŁł"
						+ "ŃńŅņŇňŌōŎŏŐőŔŕŖŗŘřŚśŜŝŞşŢţŤťŨũŪūŮůŰűŲųŴŵŶŷŹźŻżƏəƆɔ");
	}

	/** The name of the shelf that holds what has been used before. */
	public static final String RECENT = "recent";

	/**
	 * The shelf of whatever the resource pack has added.
	 *
	 * <p>Unicode keeps a range aside for characters that mean nothing to anyone but the people who
	 * agreed on them, and that is where a server's resource pack puts its own icons – a guild crest,
	 * a coin, a rank badge. Nothing can be said about them in advance, so the shelf is simply
	 * whatever the font turns out to have there, and it is empty on vanilla.
	 */
	public static final String PACK = "pack";

	private static final int PRIVATE_FIRST = 0xE000;
	private static final int PRIVATE_LAST = 0xF8FF;

	/** Built once, on a thread of its own, because it walks eleven thousand code points. */
	@Nullable
	private static volatile List<String> index;

	private Symbols() {
	}

	public static List<String> shelfNames() {
		List<String> names = new ArrayList<>();
		names.add(RECENT);
		if (!fromPack().isEmpty()) {
			names.add(PACK);
		}
		names.addAll(SHELVES.keySet());
		return names;
	}

	public static List<String> shelf(String name) {
		if (RECENT.equals(name)) {
			return drawable(QuillConfig.get().recentSymbols);
		}
		if (PACK.equals(name)) {
			return fromPack();
		}
		return split(SHELVES.getOrDefault(name, ""));
	}

	/** Worked out once and kept, because it is a six-thousand step walk through the font. */
	@Nullable
	private static List<String> pack;

	/** Every character the loaded font has in the range packs use for their own icons. */
	public static List<String> fromPack() {
		if (pack != null) {
			return pack;
		}
		List<String> found = new ArrayList<>();
		for (int codePoint = PRIVATE_FIRST; codePoint <= PRIVATE_LAST; codePoint++) {
			if (renderable(codePoint)) {
				found.add(new String(Character.toChars(codePoint)));
			}
		}
		pack = List.copyOf(found);
		return pack;
	}

	public static List<String> split(String characters) {
		List<String> out = new ArrayList<>();
		int i = 0;
		while (i < characters.length()) {
			int codePoint = characters.codePointAt(i);
			String symbol = new String(Character.toChars(codePoint));
			if (renderable(codePoint)) {
				out.add(symbol);
			}
			i += Character.charCount(codePoint);
		}
		return out;
	}

	private static List<String> drawable(List<String> symbols) {
		// Recently used is held to the stricter test: an interface picture is never something that
		// was picked on purpose, and a shelf of them is a shelf of empty cells.
		return onlySafe(symbols);
	}

	/** What a character turned out to be when the font was asked about it. */
	public enum Kind {
		/** The font has nothing for it, or what it has draws nothing at all. */
		NONE,
		/** A character the size of a character. */
		NORMAL,
		/** Something drawn far larger than a line of text: a resource pack's interface picture. */
		HUGE
	}

	/** Remembered per code point: the answer only changes when the font does. */
	private static final Map<Integer, Kind> DRAWABLE = new java.util.HashMap<>();

	/** Whether the character is worth putting on a shelf at all. */
	public static boolean renderable(int codePoint) {
		return kindOf(codePoint) != Kind.NONE;
	}

	/**
	 * Whether it is drawn far larger than a line of text.
	 *
	 * <p>Shown on the shelf as an empty cell, and only drawn – shrunk to fit – while the cursor is
	 * on it. Never remembered as recently used, because the row under the chat has no room to find
	 * out the hard way that a character is a picture of a black wall.
	 */
	public static boolean oversized(int codePoint) {
		return kindOf(codePoint) == Kind.HUGE;
	}

	/** Whether it can be put anywhere without a second thought. */
	public static boolean safe(int codePoint) {
		return kindOf(codePoint) == Kind.NORMAL;
	}

	/** The same list with the outsized characters left out, for places that draw them straight. */
	public static List<String> onlySafe(List<String> symbols) {
		List<String> out = new ArrayList<>(symbols.size());
		for (String symbol : symbols) {
			if (!symbol.isEmpty() && safe(symbol.codePointAt(0))) {
				out.add(symbol);
			}
		}
		return out;
	}

	/**
	 * Whether this character is worth offering: the font has it, and it leaves a mark.
	 *
	 * <p>Minecraft draws a character it does not have as a hollow box, and every one of those on a
	 * shelf is a place somebody will click expecting something. What the font has depends on the
	 * resource pack, so the only honest way to know is to ask it.
	 *
	 * <p>Having it is not enough, though. The font also has thin spaces, joiners and other characters
	 * it draws as nothing at all, and a button with nothing on it is worse than a box – at least the
	 * box admits there is a problem. Those are turned down here as well, by two tests: the glyph the
	 * font hands back for them is one of the built-in blanks, and Java itself calls them spacing or
	 * invisible. Characters from the private use area are exempt from the second test, because that
	 * is where resource packs put their own icons and Java has no opinion on those.
	 */
	private static Kind kindOf(int codePoint) {
		Kind known = DRAWABLE.get(codePoint);
		if (known != null) {
			return known;
		}
		Kind answer;
		try {
			net.minecraft.client.font.TextRenderer renderer =
					net.minecraft.client.MinecraftClient.getInstance().textRenderer;
			net.minecraft.client.font.FontStorage storage =
					((com.glamardor.roleplayersquill.mixin.TextRendererAccessor) renderer)
							.roleplayersquill$getFontStorage(net.minecraft.text.Style.DEFAULT_FONT_ID);
			net.minecraft.client.font.Glyph glyph = storage.getGlyph(codePoint, false);
			// Every built-in glyph is a blank of some sort: the missing box and the space.
			boolean there = !(glyph instanceof net.minecraft.client.font.BuiltinEmptyGlyph)
					&& marks(codePoint);
			answer = !there ? Kind.NONE : sane(glyph) ? Kind.NORMAL : Kind.HUGE;
		} catch (Throwable error) {
			// No font to ask – during a reload, or in a test – so only the plain test is applied.
			answer = marks(codePoint) ? Kind.NORMAL : Kind.NONE;
		}
		DRAWABLE.put(codePoint, answer);
		return answer;
	}

	/**
	 * How large the character is actually drawn, in pixels of a line of text.
	 *
	 * @return the width and the height, or null when the font cannot say
	 */
	@org.jetbrains.annotations.Nullable
	public static float[] drawnSize(int codePoint) {
		try {
			net.minecraft.client.font.TextRenderer renderer =
					net.minecraft.client.MinecraftClient.getInstance().textRenderer;
			net.minecraft.client.font.FontStorage storage =
					((com.glamardor.roleplayersquill.mixin.TextRendererAccessor) renderer)
							.roleplayersquill$getFontStorage(net.minecraft.text.Style.DEFAULT_FONT_ID);
			return measure(storage.getGlyph(codePoint, false));
		} catch (Throwable error) {
			return null;
		}
	}

	/** The largest a glyph may be drawn and still belong on a shelf, in pixels of a line of text. */
	private static final float MAX_GLYPH_WIDTH = 32.0f;
	private static final float MAX_GLYPH_HEIGHT = 24.0f;

	/**
	 * Whether this glyph is the size of a letter, rather than the size of a wall.
	 *
	 * <p>Resource packs use the private use area for two quite different things. One is icons – a
	 * coin, a crest, a key – which are letter-sized and are exactly what the shelf is for. The other
	 * is pictures for the interface: a whole panel, a background, a screen-filling black rectangle,
	 * drawn as a character with no width so that it can be pinned anywhere by writing it into a
	 * title or a scoreboard. Offering one of those to be clicked is offering to black out the
	 * screen, and once it is in the chat log it stays there until the game is restarted.
	 *
	 * <p>So the drawn size is asked for, not the advance: these things are deliberately built to
	 * take up no width at all while covering everything.
	 */
	private static boolean sane(net.minecraft.client.font.Glyph glyph) {
		float[] size = measure(glyph);
		return size == null || size[0] <= MAX_GLYPH_WIDTH && size[1] <= MAX_GLYPH_HEIGHT;
	}

	/**
	 * How large a glyph is actually drawn, in pixels of a line of text.
	 *
	 * <p>Asking is more awkward than it should be. A glyph loaded from a bitmap does not itself say
	 * how big it is – it hands the size over only while being baked into the atlas, to whatever is
	 * doing the baking. So the way to find out is to start baking it and keep what it passes along;
	 * nothing is uploaded, because that is the baker's job and this one does not do it.
	 *
	 * @return the width and the height, or null when the glyph will not say
	 */
	@org.jetbrains.annotations.Nullable
	private static float[] measure(net.minecraft.client.font.Glyph glyph) {
		net.minecraft.client.font.RenderableGlyph drawn = null;
		if (glyph instanceof net.minecraft.client.font.RenderableGlyph direct) {
			drawn = direct;
		} else {
			net.minecraft.client.font.RenderableGlyph[] caught = new net.minecraft.client.font.RenderableGlyph[1];
			try {
				glyph.bake(shape -> {
					caught[0] = shape;
					return null;
				});
			} catch (Throwable error) {
				// A glyph that will not be baked out of turn keeps its size to itself.
			}
			drawn = caught[0];
		}
		if (drawn == null) {
			return null;
		}
		float scale = drawn.getOversample();
		if (scale <= 0.0f) {
			return null;
		}
		return new float[] {drawn.getWidth() / scale, drawn.getHeight() / scale};
	}

	/** Whether the character is one that draws something, rather than a space or a control. */
	private static boolean marks(int codePoint) {
		if (codePoint == '§') {
			// The section sign is the one character that can never be offered. The font has it and
			// Java calls it punctuation, but nothing ever draws it: it swallows the character after
			// it as a formatting code, and on its own at the end of a string it is dropped. In chat
			// it is worse than useless – the server treats it as an illegal character.
			return false;
		}
		if (codePoint >= PRIVATE_FIRST && codePoint <= PRIVATE_LAST) {
			return true;
		}
		if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
			return false;
		}
		return switch (Character.getType(codePoint)) {
			case Character.UNASSIGNED, Character.CONTROL, Character.FORMAT,
					Character.SURROGATE, Character.PRIVATE_USE -> false;
			default -> true;
		};
	}

	/** Forgets which characters the font had. Call when the resource packs change. */
	public static void forgetFont() {
		DRAWABLE.clear();
		pack = null;
		index = null;
	}

	/** True once the search has something to search. */
	public static boolean indexed() {
		return index != null;
	}

	/**
	 * Russian words for the English ones the Unicode names are written in.
	 *
	 * <p>The names come from the runtime and are all in English, which is no use to anybody typing
	 * «стрелка». Rather than ship eleven thousand translations, the query is translated: one word
	 * of Russian becomes one word of English and the search runs as it always did. Anything not in
	 * this list is searched for as typed, so English still works.
	 */
	private static final Map<String, String> RUSSIAN = new LinkedHashMap<>();

	static {
		RUSSIAN.put("стрелк", "ARROW");
		RUSSIAN.put("стрел", "ARROW");
		RUSSIAN.put("влево", "LEFTWARDS");
		RUSSIAN.put("вправо", "RIGHTWARDS");
		RUSSIAN.put("вверх", "UPWARDS");
		RUSSIAN.put("вниз", "DOWNWARDS");
		RUSSIAN.put("сердц", "HEART");
		RUSSIAN.put("звезд", "STAR");
		RUSSIAN.put("звёзд", "STAR");
		RUSSIAN.put("солнц", "SUN");
		RUSSIAN.put("луна", "MOON");
		RUSSIAN.put("облак", "CLOUD");
		RUSSIAN.put("дожд", "RAIN");
		RUSSIAN.put("снеж", "SNOW");
		RUSSIAN.put("снег", "SNOW");
		RUSSIAN.put("зонт", "UMBRELLA");
		RUSSIAN.put("цвет", "FLOWER");
		RUSSIAN.put("лист", "LEAF");
		RUSSIAN.put("дерев", "TREE");
		RUSSIAN.put("гора", "MOUNTAIN");
		RUSSIAN.put("огон", "FIRE");
		RUSSIAN.put("молни", "LIGHTNING");
		RUSSIAN.put("вода", "WATER");
		RUSSIAN.put("кирк", "PICK");
		RUSSIAN.put("кайл", "PICK");
		RUSSIAN.put("молот", "HAMMER");
		RUSSIAN.put("меч", "SWORD");
		RUSSIAN.put("щит", "SHIELD");
		RUSSIAN.put("якор", "ANCHOR");
		RUSSIAN.put("цеп", "CHAIN");
		RUSSIAN.put("шестер", "GEAR");
		RUSSIAN.put("весы", "SCALES");
		RUSSIAN.put("колб", "ALEMBIC");
		RUSSIAN.put("корон", "CROWN");
		RUSSIAN.put("ключ", "KEY");
		RUSSIAN.put("замок", "LOCK");
		RUSSIAN.put("череп", "SKULL");
		RUSSIAN.put("гроб", "COFFIN");
		RUSSIAN.put("урна", "URN");
		RUSSIAN.put("крест", "CROSS");
		RUSSIAN.put("галочк", "CHECK");
		RUSSIAN.put("галк", "CHECK");
		RUSSIAN.put("ножниц", "SCISSORS");
		RUSSIAN.put("конверт", "ENVELOPE");
		RUSSIAN.put("письм", "ENVELOPE");
		RUSSIAN.put("каранд", "PENCIL");
		RUSSIAN.put("перо", "NIB");
		RUSSIAN.put("часы", "CLOCK");
		RUSSIAN.put("песочн", "HOURGLASS");
		RUSSIAN.put("телефон", "TELEPHONE");
		RUSSIAN.put("дом", "HOUSE");
		RUSSIAN.put("церк", "CHURCH");
		RUSSIAN.put("палат", "TENT");
		RUSSIAN.put("кораб", "SAILBOAT");
		RUSSIAN.put("лодк", "SAILBOAT");
		RUSSIAN.put("самолет", "AIRPLANE");
		RUSSIAN.put("самолёт", "AIRPLANE");
		RUSSIAN.put("круг", "CIRCLE");
		RUSSIAN.put("квадрат", "SQUARE");
		RUSSIAN.put("треуг", "TRIANGLE");
		RUSSIAN.put("ромб", "DIAMOND");
		RUSSIAN.put("точк", "DOT");
		RUSSIAN.put("линия", "LINE");
		RUSSIAN.put("черта", "LINE");
		RUSSIAN.put("рамк", "BOX DRAWINGS");
		RUSSIAN.put("угол", "BOX DRAWINGS");
		RUSSIAN.put("блок", "BLOCK");
		RUSSIAN.put("тень", "SHADE");
		RUSSIAN.put("нота", "NOTE");
		RUSSIAN.put("ноты", "NOTE");
		RUSSIAN.put("музык", "MUSICAL");
		RUSSIAN.put("карт", "CARD");
		RUSSIAN.put("пик", "SPADE");
		RUSSIAN.put("черв", "HEART");
		RUSSIAN.put("буб", "DIAMOND");
		RUSSIAN.put("треф", "CLUB");
		RUSSIAN.put("кост", "DIE FACE");
		RUSSIAN.put("шахмат", "CHESS");
		RUSSIAN.put("корол", "KING");
		RUSSIAN.put("ферз", "QUEEN");
		RUSSIAN.put("ладь", "ROOK");
		RUSSIAN.put("кон", "KNIGHT");
		RUSSIAN.put("пешк", "PAWN");
		RUSSIAN.put("рун", "RUNIC");
		RUSSIAN.put("греч", "GREEK");
		RUSSIAN.put("дроб", "FRACTION");
		RUSSIAN.put("градус", "DEGREE");
		RUSSIAN.put("рубл", "RUBLE");
		RUSSIAN.put("евро", "EURO");
		RUSSIAN.put("доллар", "DOLLAR");
		RUSSIAN.put("фунт", "POUND");
		RUSSIAN.put("валют", "CURRENCY");
		RUSSIAN.put("кавыч", "QUOTATION");
		RUSSIAN.put("тире", "DASH");
		RUSSIAN.put("дефис", "HYPHEN");
		RUSSIAN.put("скобк", "BRACKET");
		RUSSIAN.put("звезд", "STAR");
		RUSSIAN.put("цифр", "DIGIT");
		RUSSIAN.put("номер", "NUMERO");
		RUSSIAN.put("параграф", "SECTION SIGN");
		RUSSIAN.put("сноск", "DAGGER");
		RUSSIAN.put("кинжал", "DAGGER");
		RUSSIAN.put("стрелоч", "ARROW");
		RUSSIAN.put("рука", "HAND");
		RUSSIAN.put("палец", "HAND");
		RUSSIAN.put("глаз", "EYE");
		RUSSIAN.put("знак", "SIGN");
		RUSSIAN.put("предупр", "WARNING");
		RUSSIAN.put("радиац", "RADIOACTIVE");
		RUSSIAN.put("яд", "SKULL");
		RUSSIAN.put("лилия", "FLEUR");
		RUSSIAN.put("инь", "YIN YANG");
		RUSSIAN.put("зодиак", "ZODIAC");
	}

	/**
	 * Every English term a query could mean.
	 *
	 * <p>Matched both ways round, so half a word works: «чере» is the start of «череп» and finds the
	 * skull, and «черепа» starts with «череп» and finds it too. Typing a word only to have it match
	 * on the last letter is the sort of search that feels broken while it is working.
	 */
	private static List<String> terms(String query) {
		String lower = query.toLowerCase(Locale.ROOT);
		List<String> found = new ArrayList<>();
		for (Map.Entry<String, String> entry : RUSSIAN.entrySet()) {
			if (lower.startsWith(entry.getKey()) || entry.getKey().startsWith(lower)) {
				if (!found.contains(entry.getValue())) {
					found.add(entry.getValue());
				}
			}
		}
		if (found.isEmpty()) {
			found.add(query.toUpperCase(Locale.ROOT));
		}
		return found;
	}

	/** Matches the character itself, its Unicode name in either language, or its code in hex. */
	public static List<String> find(String query, int limit) {
		List<String> needles = terms(query);
		String hexNeedle = query.toUpperCase(Locale.ROOT);
		List<String> all = index;
		List<String> out = new ArrayList<>();
		if (all == null) {
			return out;
		}
		for (String candidate : all) {
			if (out.size() >= limit) {
				break;
			}
			int codePoint = candidate.codePointAt(0);
			if (!renderable(codePoint)) {
				continue;
			}
			if (candidate.equals(query)) {
				out.add(0, candidate);
				continue;
			}
			String hex = Integer.toHexString(codePoint).toUpperCase(Locale.ROOT);
			if (hex.equals(hexNeedle) || ("U+" + hex).equals(hexNeedle)) {
				out.add(0, candidate);
				continue;
			}
			String name = Character.getName(codePoint);
			if (name == null) {
				continue;
			}
			for (String needle : needles) {
				if (name.contains(needle)) {
					out.add(candidate);
					break;
				}
			}
		}
		return out;
	}

	/** Starts reading the character names, if that has not happened yet. */
	public static void prepare() {
		if (index != null) {
			return;
		}
		Thread thread = new Thread(() -> {
			List<String> found = new ArrayList<>(12000);
			// Punctuation, symbols, arrows, maths, box drawing, blocks, shapes, dingbats, braille,
			// plus the runic block. Everything a book is likely to want and nothing that is a letter
			// in a language the keyboard already types.
			addRange(found, 0x00A1, 0x02FF);
			addRange(found, 0x0370, 0x03FF);
			addRange(found, 0x16A0, 0x16FF);
			addRange(found, 0x2010, 0x2BFF);
			addRange(found, 0xA700, 0xA7FF);
			index = List.copyOf(found);
		}, "roleplayers-quill-symbol-index");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Adds a block of code points to the search index.
	 *
	 * <p>Without asking the font whether it has them: this runs on a thread of its own, and the font
	 * belongs to the one that draws. The ones it cannot draw are dropped later, when the results are
	 * shown, which happens where it is safe to ask.
	 */
	private static void addRange(List<String> found, int from, int to) {
		for (int codePoint = from; codePoint <= to; codePoint++) {
			if (Character.isDefined(codePoint) && !Character.isISOControl(codePoint)
					&& Character.getType(codePoint) != Character.UNASSIGNED) {
				found.add(new String(Character.toChars(codePoint)));
			}
		}
	}

	/** The caption under the grid: the code point and, where the runtime knows it, the name. */
	public static String describe(String symbol) {
		int codePoint = symbol.codePointAt(0);
		String name = Character.getName(codePoint);
		return String.format("U+%04X", codePoint) + (name == null ? "" : "  " + name);
	}
}
