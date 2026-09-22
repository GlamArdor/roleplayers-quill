package com.glamardor.roleplayersquill.config;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.text.Alignment;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.Paginator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain-old-data settings, written to {@code config/roleplayersquill.json}.
 *
 * <p>Public fields with no getters on purpose: both settings screens bind straight to them, the
 * reset walks them with reflection, and Gson round-trips them without a single adapter.
 */
public class QuillConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static QuillConfig instance;

	/**
	 * Bumped when a default changes in a way an existing file must not keep overriding.
	 *
	 * <p>Zero here, not the current number: Gson runs the field initialisers and then overwrites
	 * only what the file actually contains, so a file written before this field existed arrives
	 * with a zero – which is exactly the file that needs migrating.
	 */
	public int configVersion;

	private static final int CURRENT_VERSION = 1;

	// ---- the editor ------------------------------------------------------------------------------

	/** Take over the vanilla book editor. Off leaves the game exactly as it was. */
	public boolean replaceBookEditor = true;
	/** How much bigger to draw the book while editing. The vanilla page is small. */
	public float editorScale = 1.0f;
	/** Show the character count and the page's remaining room while typing. */
	public boolean showCounter = true;
	/** Draw the page edge and the line grid, so alignment can be seen rather than guessed at. */
	public boolean showGuides = false;
	/** Typing after a formatted word keeps that formatting, the way a word processor does. */
	public boolean carryFormatting = true;
	/** What a new paragraph starts as. */
	public Alignment defaultAlignment = Alignment.LEFT;

	// ---- filling the page ------------------------------------------------------------------------

	/** Break words across lines so the text sits close. Off by default: it changes how prose reads. */
	public boolean hyphenate = false;
	/** Letters that must stay behind on the line before a hyphen. */
	public int hyphenMinBefore = 2;
	/** Letters that must be carried over after it. */
	public int hyphenMinAfter = 2;
	/** Break the word a page ends inside, rather than moving it down whole. */
	public boolean hyphenateAtPageBreak = false;
	/** Stretch the last line of a justified paragraph too. Typography says not to; some books do. */
	public boolean justifyLastLine = false;

	// ---- correcting as you type ------------------------------------------------------------------

	/**
	 * Whether anything is put right while it is being typed.
	 *
	 * <p>On, but rule by rule underneath and with a button in the editor itself – because when a
	 * correction is unwanted it is unwanted right then, with the sentence still on the screen, and
	 * a switch that lives in another window is a switch nobody reaches in time.
	 */
	public boolean autoCorrect = true;
	/** Two hyphens become a dash, three become a long one. */
	public boolean autoDashes = true;
	/** Straight quotes become the angled pair a Russian book uses. */
	public boolean autoQuotes = true;
	/** Three dots become one character, which is narrower and never breaks across a line. */
	public boolean autoEllipsis = true;
	/** The first letter of a sentence is made a capital. */
	public boolean autoCapitals = false;
	/** (c), (r) and (tm) become their signs. */
	public boolean autoSigns = true;
	/** A straight apostrophe becomes a curled one. */
	public boolean autoApostrophe = true;

	// ---- spelling ---------------------------------------------------------------------------------

	/**
	 * Underline words no dictionary knows.
	 *
	 * <p>Off until it is asked for, because asking for it is what fetches the word lists – nineteen
	 * megabytes for the two of them, and nothing is downloaded before somebody presses the button.
	 */
	public boolean spellCheck = false;
	/** Check Russian words against the Russian list. */
	public boolean spellRussian = true;
	/** Check English words against the English list. */
	public boolean spellEnglish = true;
	/**
	 * Leave words that begin with a capital alone.
	 *
	 * <p>On, and it is the setting that decides whether any of this is usable on a roleplaying
	 * server: a chapter carries a dozen invented names, no dictionary holds one of them, and a
	 * checker that marks them all is a checker nobody keeps switched on.
	 */
	public boolean spellSkipCapitals = true;
	/** Fetch the word lists the first time the check is switched on. */
	public boolean spellAutoDownload = true;
	/**
	 * Check the chat box and the sign editor as well as books.
	 *
	 * <p>The same dictionary and the same menu of corrections: a line of roleplay is a line of
	 * roleplay whether it is being written into a book or said out loud in the chat, and the chat is
	 * where the typing is fastest and least looked over.
	 */
	public boolean spellElsewhere = true;

	// ---- pasting and importing -------------------------------------------------------------------

	/** A paste longer than the page flows into as many pages as it needs, with no prompt. */
	public boolean autoPasteMultiPage = true;
	/** Ask before a paste adds pages, instead of just doing it. */
	public boolean confirmBigPaste = true;
	/** On import, only a blank line ends a paragraph – what a hard-wrapped text file wants. */
	public boolean importJoinLines = true;
	/** Where exports land, relative to the game folder. */
	public String exportFolder = "quill-books";

	// ---- links, tooltips and the rich tier -------------------------------------------------------

	/**
	 * When to write a page as a text component instead of a string.
	 *
	 * <p>{@link RichMode#AUTO} uses it only where it can: in creative, where the client is allowed
	 * to hand the server a finished item. Everywhere else the page is written with {@code §} codes
	 * and spaces, which anyone can read with no mod at all.
	 */
	public RichMode richMode = RichMode.AUTO;
	/** Colour and underline links while editing, so they can be told from the text around them. */
	public boolean highlightLinks = true;
	/** Make a bare address in any book clickable, mod-written or not. */
	public boolean readerLinks = true;
	/** Also recognise addresses without a scheme, like {@code example.com/page}. */
	public boolean readerLinksWithoutScheme = true;

	// ---- reading a signed book ---------------------------------------------------------------------

	/**
	 * The buttons beside a signed book, and everything they open: finding, contents, the page
	 * list, saving a copy and exporting. Off leaves the vanilla book screen exactly as it was,
	 * addresses aside.
	 */
	public boolean readerTools = true;
	/** The title, the author and which copy this is, in the book's own top border. */
	public boolean readerHeader = true;

	// ---- the other places text is typed ----------------------------------------------------------

	public boolean signEditor = true;
	public boolean anvilEditor = true;
	/** A row of symbols over the chat box, with a button for the full browser. */
	public boolean chatSymbols = true;
	/**
	 * Formatting codes over the chat box as well.
	 *
	 * <p>Off by default. The chat is the one place a section sign cannot go – the server checks
	 * every message for one and disconnects the client that sends it – so all a code button can do
	 * here is insert whatever character the server's own colour plugin reads instead, and that is a
	 * guess about somebody else's plugin rather than something this mod can promise.
	 */
	public boolean chatFormatting = false;
	/** What the chat and anvil code buttons insert. Never a section sign; see above. */
	public String chatCodePrefix = "&";

	// ---- voice typing -----------------------------------------------------------------------------

	/** Off until asked for, and the model is not downloaded until it is. */
	public boolean voiceEnabled = false;
	/** Which model to dictate with; see {@code SpeechModels}. */
	public String voiceModel = "ru-giga-rnnt";
	/** Fetch the engine and the model the first time dictation is used. */
	public boolean voiceAutoDownload = true;
	/** Name of the microphone to record from; empty means whatever the system offers first. */
	public String voiceDevice = "";
	/** Capitalise the first letter of what is dictated and end it with a full stop. */
	public boolean voiceTidyUp = true;
	/** Hold the key to talk, rather than pressing it once to start and again to stop. */
	public boolean voicePushToTalk = true;

	// ---- symbols ----------------------------------------------------------------------------------

	/** Symbols the player has picked before, newest first. */
	public java.util.List<String> recentSymbols = new java.util.ArrayList<>();

	public enum RichMode {
		OFF, AUTO, ALWAYS;

		public RichMode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public net.minecraft.text.Text label() {
			return net.minecraft.text.Text.translatable("roleplayersquill.rich." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	// ---- derived ----------------------------------------------------------------------------------

	public Layout.Options layoutOptions() {
		return new Layout.Options(hyphenate, justifyLastLine, hyphenMinBefore, hyphenMinAfter);
	}

	public Paginator.Options paginatorOptions() {
		return Paginator.Options.of(layoutOptions(), hyphenateAtPageBreak);
	}

	// ---- plumbing ---------------------------------------------------------------------------------

	public static QuillConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(RoleplayersQuill.MOD_ID + ".json");
	}

	private static QuillConfig load() {
		Path path = path();
		QuillConfig config = new QuillConfig();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				QuillConfig read = GSON.fromJson(reader, QuillConfig.class);
				if (read != null) {
					config = read;
				}
			} catch (IOException | RuntimeException error) {
				RoleplayersQuill.LOGGER.error("Could not read {}, starting from the defaults", path, error);
			}
		}
		config.migrate();
		config.clamp();
		return config;
	}

	private void migrate() {
		if (configVersion >= CURRENT_VERSION) {
			return;
		}
		configVersion = CURRENT_VERSION;
	}

	private void clamp() {
		editorScale = Math.max(1.0f, Math.min(2.5f, editorScale));
		hyphenMinBefore = Math.max(1, Math.min(5, hyphenMinBefore));
		hyphenMinAfter = Math.max(1, Math.min(5, hyphenMinAfter));
		if (chatCodePrefix == null || chatCodePrefix.isEmpty()) {
			chatCodePrefix = "&";
		}
		chatCodePrefix = chatCodePrefix.substring(0, 1);
		if (exportFolder == null || exportFolder.isBlank()) {
			exportFolder = "quill-books";
		}
		if (defaultAlignment == null) {
			defaultAlignment = Alignment.LEFT;
		}
		if (richMode == null) {
			richMode = RichMode.AUTO;
		}
		if (voiceModel == null || voiceModel.isBlank()) {
			voiceModel = "ru-giga-rnnt";
		}
		if (recentSymbols == null) {
			recentSymbols = new java.util.ArrayList<>();
		}
		while (recentSymbols.size() > 64) {
			recentSymbols.remove(recentSymbols.size() - 1);
		}
	}

	public void rememberSymbol(String symbol) {
		// Not the outsized ones. They are a resource pack's interface pictures rather than
		// characters, and the row under the chat draws what it holds without asking twice.
		if (symbol.isEmpty() || com.glamardor.roleplayersquill.screen.Symbols.oversized(symbol.codePointAt(0))) {
			return;
		}
		recentSymbols.remove(symbol);
		recentSymbols.add(0, symbol);
		clamp();
		save();
	}

	public void save() {
		clamp();
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException error) {
			RoleplayersQuill.LOGGER.error("Could not write {}", path, error);
		}
	}

	/**
	 * Everything back to what a fresh install would have, except the symbols the player has used:
	 * those are a record of their own work, not a setting.
	 */
	public void resetToDefaults() {
		QuillConfig fresh = new QuillConfig();
		for (Field field : QuillConfig.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			String name = field.getName();
			if (name.equals("configVersion") || name.equals("recentSymbols")) {
				continue;
			}
			try {
				field.set(this, field.get(fresh));
			} catch (IllegalAccessException error) {
				RoleplayersQuill.LOGGER.warn("Could not reset {}", name, error);
			}
		}
	}
}
