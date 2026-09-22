package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.RoleplayersQuillClient;
import com.glamardor.roleplayersquill.book.BookIO;
import com.glamardor.roleplayersquill.book.BookSender;
import com.glamardor.roleplayersquill.book.DictionaryDownload;
import com.glamardor.roleplayersquill.book.FileDialogs;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.speech.Dictation;
import com.glamardor.roleplayersquill.text.Alignment;
import com.glamardor.roleplayersquill.text.Layout;
import com.glamardor.roleplayersquill.text.LegacyCodec;
import com.glamardor.roleplayersquill.text.ListStyle;
import com.glamardor.roleplayersquill.text.Paginator;
import com.glamardor.roleplayersquill.text.Paragraph;
import com.glamardor.roleplayersquill.text.QuillDocument;
import com.glamardor.roleplayersquill.text.QuillStyle;
import com.glamardor.roleplayersquill.text.Spelling;
import com.glamardor.roleplayersquill.text.BookTools;
import com.glamardor.roleplayersquill.text.TableBuilder;
import com.glamardor.roleplayersquill.text.Widths;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The book editor.
 *
 * <p>It draws the page exactly as the finished book will look, because it draws it from the same
 * layout the page is written out with: the alignment you see is the alignment the spaces will
 * make, and the line the text wraps on is the line it will wrap on for a reader who has never heard
 * of this mod.
 */
public class QuillEditScreen extends Screen {
	/** Where the text sits inside the 192 by 192 book, taken from the vanilla reader. */
	private static final int TEXT_X = 36;
	private static final int TEXT_Y = 30;
	private static final int BOOK_SIZE = 192;

	/**
	 * The page-turn arrows, where vanilla puts them.
	 *
	 * <p>Drawn inside the book's own matrix and hit-tested in the book's own coordinates rather
	 * than added as widgets: a widget would keep its size while the book around it grew, and the
	 * whole point of the page size setting is that the book grows.
	 */
	private static final int ARROW_PREVIOUS_X = 43;
	private static final int ARROW_NEXT_X = 116;
	private static final int ARROW_Y = 159;
	private static final int ARROW_WIDTH = 23;
	private static final int ARROW_HEIGHT = 13;

	/** The very sprites {@code PageTurnWidget} draws. */
	private static final Identifier PAGE_FORWARD = Identifier.ofVanilla("widget/page_forward");
	private static final Identifier PAGE_FORWARD_HIGHLIGHTED = Identifier.ofVanilla("widget/page_forward_highlighted");
	private static final Identifier PAGE_BACKWARD = Identifier.ofVanilla("widget/page_backward");
	private static final Identifier PAGE_BACKWARD_HIGHLIGHTED = Identifier.ofVanilla("widget/page_backward_highlighted");

	private static final int INK = 0xFF000000;
	private static final int INK_FADED = 0xFF7A7060;
	private static final int SELECTION = 0x663C6390;
	private static final int GUIDE = 0x22000000;
	private static final int LINK_INK = 0xFF1F4FA0;

	private final ItemStack stack;
	private final Hand hand;
	private final PageEditor editor;
	/** The pages as they were when the book was opened, so an untouched book sends nothing. */
	private final List<String> original;

	private float scale = 1.0f;
	private int bookX;
	private int bookY;
	private int toolbarHeight;

	private int blink;
	private boolean dragging;
	private long lastClickTime;
	private int clickCount;

	/** Whether the brush is waiting for a selection to paint. */
	private boolean brushArmed;
	/** The selection the brush took its style from, which is not the one it is waiting for. */
	private PageEditor.Span brushFrom = PageEditor.Span.EMPTY;
	private int brushPage;

	private final List<IconButton> tools = new ArrayList<>();
	@Nullable
	private IconButton colourButton;

	/** The character browser, docked to the left when it is open, with everything else shifted over. */
	@Nullable
	private SymbolPanel symbolPanel;
	private boolean symbolsOpen;
	/** How much of the left edge the panel has taken, and everything else has to keep out of. */
	private int leftDock;

	/** The palette, which hangs under its own button rather than taking over the screen. */
	@Nullable
	private ColourPopup colourPopup;
	@Nullable
	private CorrectPopup correctPopup;
	@Nullable
	private IconButton correctButton;
	@Nullable
	private StylePopup stylePopup;
	@Nullable
	private IconButton styleButton;
	@Nullable
	private OrnamentPopup ornamentPopup;
	@Nullable
	private IconButton ornamentButton;
	/** The menu over a misspelled word, and where on the screen it was opened. */
	@Nullable
	private SpellPopup spellPopup;
	private int spellX;
	private int spellY;
	@Nullable
	private Text notice;
	private long noticeUntil;
	private boolean fontWarned;
	/** Whether the book has already been looked over for ink an older version left on it. */
	private boolean mendOffered;
	/** Whether the unbreakable blank has been explained once, which is as often as it needs to be. */
	private boolean heldBlankExplained;
	/** The find strip under the book, when it is open. Null is closed. */
	@Nullable
	private FindBar findBar;
	/** Where the row with Sign and Done sits, so other things can be put under it. */
	private int buttonsY;

	public QuillEditScreen(ItemStack stack, Hand hand, List<String> pages) {
		super(Text.translatable("roleplayersquill.editor.title"));
		this.stack = stack;
		this.hand = hand;
		this.original = List.copyOf(pages);

		// A book this client wrote before is remembered in full – paragraphs, links, colours. One it
		// has not seen is read back from the codes, which is everything a page can actually carry.
		QuillDocument document = BookIO.loadDraft(pages, ownerTag(hand));
		if (document == null) {
			// No draft under these exact pages. Before treating it as a book never seen before, ask
			// whether it is a book we know that has been changed from outside – which on this server
			// happens every time a page is torn out of one.
			document = BookIO.reopen(pages, QuillConfig.get().layoutOptions());
		}
		if (document == null) {
			document = new QuillDocument();
			document.pages().clear();
			if (pages.isEmpty()) {
				document.pages().add(QuillDocument.newPage());
			} else {
				for (String page : pages) {
					document.pages().add(LegacyCodec.decode(page));
				}
			}
		}
		// Read once more, whatever the document itself came from, purely to file each page under what
		// it reads as. A page nobody edits is written back as this very string rather than as this
		// mod's idea of it – see QuillDocument.rememberSource.
		for (String page : pages) {
			document.rememberSource(LegacyCodec.decode(page), page);
		}
		this.editor = new PageEditor(document);
		this.draftKey = List.copyOf(pages);
		// A blank book is never opened as somebody's unfinished work, but if there is any, it is
		// worth saying so: the alternative is that a crash quietly costs an evening.
		this.recovery = BookIO.isBlank(pages) ? BookIO.blankDraft(ownerTag(hand)) : null;
	}

	/**
	 * Who is holding this book and where, which is the only thing that tells two blank books apart.
	 *
	 * <p>The player and the place it is being held from. Two empty books in two hands are two books;
	 * two empty books one after the other in the same hand are, as far as anything here can tell, the
	 * same one – which is why a draft for a blank book also has to be recent to be offered back.
	 */
	private static String ownerTag(Hand hand) {
		var player = MinecraftClient.getInstance().player;
		if (player == null) {
			return "";
		}
		int slot = hand == Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40;
		return player.getUuidAsString() + ":" + slot;
	}

	/**
	 * The book as the server holds it, which is what a draft is filed under.
	 *
	 * <p>Not the book as it stands: the draft has to be found again by whoever opens this item next,
	 * and all they will have to go on is what the server hands them. It moves on only when the book
	 * is really sent, because that is the moment the server's copy changes.
	 */
	private List<String> draftKey;
	/** When the draft was last written, so that typing does not write a file per letter. */
	private long draftWritten;
	/** Unsaved work on a blank book, waiting to be offered. Cleared once taken or refused. */
	@Nullable
	private QuillDocument recovery;

	// ---- setting up ------------------------------------------------------------------------------

	@Override
	protected void init() {
		Widths.clear();
		warnAboutFont();
		offerToMend();
		// The dictionary is twelve megabytes off a disk and is read on a thread of its own; asking
		// for it here means it is usually in by the time the first page has been read.
		if (QuillConfig.get().spellCheck) {
			// Forgotten rather than kept, because the way back into this screen is usually from the
			// settings, and what was just changed there is what a remembered answer would contradict.
			Spelling.forget();
			Spelling.load();
		}
		tools.clear();

		List<List<IconButton>> groups = buildTools();
		layoutTools(groups);

		// The symbol strip lies under everything and takes its room off the bottom, so that opening
		// it never moves the page: whatever is on screen stays exactly where it was.
		int stripHeight = symbolsOpen ? Math.max(SymbolPanel.minimumHeight(), height / 4) : 0;

		// Eleven pixels between the toolbar and the book, which is where the counter goes. Inside the
		// book there is only room for one line of small print, and vanilla already spends it on the
		// page number.
		float down = (height - toolbarHeight - 62 - stripHeight) / (float) BOOK_SIZE;
		float across = (width - 8) / (float) BOOK_SIZE;
		float room = Math.min(down, across);
		scale = MathHelper.clamp(QuillConfig.get().editorScale, 0.6f, Math.max(0.6f, room));
		bookX = (int) ((width - BOOK_SIZE * scale) / 2);
		bookY = toolbarHeight + 12;

		int centre = width / 2;
		// The offer to recover unsaved work and the find strip each stand on a row of their own under
		// the two buttons, so room is kept for them here rather than dropped on top of them.
		int offerRow = (recovery != null ? 22 : 0) + (findBar != null ? FindBar.height() + 8 : 0);
		buttonsY = Math.min(height - 24 - stripHeight - offerRow,
				(int) (bookY + BOOK_SIZE * scale) + 4);

		if (symbolsOpen) {
			if (symbolPanel == null) {
				symbolPanel = new SymbolPanel(this::insertSymbol);
			}
			// Directly under the row of buttons and exactly as wide as it, so the two read as one
			// block rather than as a strip that happens to be near them.
			int stripWidth = Math.min(SymbolPanel.WIDTH, width - 8);
			int stripY = Math.min(buttonsY + 24, height - stripHeight - 2);
			symbolPanel.layout(centre - stripWidth / 2, stripY, stripWidth,
					Math.min(stripHeight, height - stripY - 2), this::addDrawableChild, textRenderer);
			symbolPanel.reload();
		}
		// Two buttons, the same two the vanilla editor has. There was a third that showed the page as
		// it would be read, and it was worth less than it cost: the page under the caret is already
		// drawn the way the reader will see it, so the preview only ever repeated it – and a preview
		// that repeats the editor is a second opinion from the same witness.
		addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.editor.sign"),
						button -> client.setScreen(new SignBookScreen(this, editor, stack, hand)))
				.dimensions(centre - 102, buttonsY, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> saveAndClose())
				.dimensions(centre + 2, buttonsY, 100, 20).build());

		if (recovery != null) {
			int pages = recovery.pageCount();
			addDrawableChild(ButtonWidget.builder(
					Text.translatable("roleplayersquill.recover.offer", pages), button -> {
						editor.document().mark();
						editor.document().pages().clear();
						for (List<Paragraph> page : recovery.pages()) {
							editor.document().pages().add(QuillDocument.copyPage(page));
						}
						editor.setPage(0);
						editor.touch();
						recovery = null;
						clearAndInit();
					}).dimensions(centre - 102, buttonsY + 22, 150, 18).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("roleplayersquill.recover.no"), button -> {
				recovery = null;
				clearAndInit();
			}).dimensions(centre + 52, buttonsY + 22, 50, 18).build());
		}

		if (findBar != null) {
			findBar.layout(centre, buttonsY + 22 + (recovery != null ? 22 : 0), width, textRenderer,
					this::addDrawableChild);
		}

		// Last, so its box and its buttons are drawn over everything else.
		if (colourPopup != null && colourButton != null) {
			colourPopup.layout(colourButton.getX(), colourButton.getY() + IconButton.SIZE + 1,
					width, height, this::addDrawableChild, textRenderer);
		}
		if (correctPopup != null && correctButton != null) {
			correctPopup.layout(correctButton.getX(), correctButton.getY() + IconButton.SIZE + 1,
					width, height, this::addDrawableChild, textRenderer);
		}
		if (stylePopup != null && styleButton != null) {
			stylePopup.layout(styleButton.getX(), styleButton.getY() + IconButton.SIZE + 1, width, height);
		}
		if (ornamentPopup != null && ornamentButton != null) {
			ornamentPopup.layout(ornamentButton.getX(), ornamentButton.getY() + IconButton.SIZE + 1,
					width, height);
		}
		if (spellPopup != null) {
			// Down to the row of buttons and no further: below that is Sign and Done, which are
			// widgets and are drawn over anything that reaches them.
			spellPopup.layout(spellX, spellY, width, buttonsY - 2, textRenderer);
		}
	}

	private void insertSymbol(String symbol) {
		editor.insert(symbol);
		editor.touch();
	}

	/** The toolbar, in groups that are kept together when the rows wrap. */
	private List<List<IconButton>> buildTools() {
		List<List<IconButton>> groups = new ArrayList<>();

		groups.add(List.of(
				tool(Icons.BOLD, "bold", () -> editor.toggle(QuillStyle::bold, QuillStyle::withBold))
						.showing(() -> editor.selectionAll(QuillStyle::bold)),
				tool(Icons.ITALIC, "italic", () -> editor.toggle(QuillStyle::italic, QuillStyle::withItalic))
						.showing(() -> editor.selectionAll(QuillStyle::italic)),
				tool(Icons.UNDERLINE, "underline", () -> editor.toggle(QuillStyle::underlined, QuillStyle::withUnderlined))
						.showing(() -> editor.selectionAll(QuillStyle::underlined)),
				tool(Icons.STRIKE, "strike", () -> editor.toggle(QuillStyle::strikethrough, QuillStyle::withStrikethrough))
						.showing(() -> editor.selectionAll(QuillStyle::strikethrough)),
				tool(Icons.OBFUSCATED, "obfuscated", () -> editor.toggle(QuillStyle::obfuscated, QuillStyle::withObfuscated))
						.showing(() -> editor.selectionAll(QuillStyle::obfuscated))));

		colourButton = tool(Icons.COLOR_SWATCH, "colour", this::toggleColours)
				.showing(() -> colourPopup != null);
		correctButton = tool(Icons.CORRECT, "correct", this::toggleCorrect)
				.showing(() -> QuillConfig.get().autoCorrect);
		groups.add(List.of(
				colourButton,
				tool(Icons.BRUSH, "brush", this::brush).showing(() -> editor.brush() != null),
				tool(Icons.CLEAR, "clear_format", editor::clearFormatting),
				correctButton,
				tool(Icons.SPELL, "spell", this::toggleSpell)
						.showing(() -> QuillConfig.get().spellCheck)
						.telling(this::spellTooltip)));

		styleButton = tool(Icons.PARAGRAPH, "paragraph", this::toggleStyles)
				.showing(() -> stylePopup != null);
		groups.add(List.of(
				styleButton,
				tool(Icons.ALIGN_LEFT, "align_left", () -> editor.setAlignment(Alignment.LEFT))
						.showing(() -> editor.activeAlignment() == Alignment.LEFT),
				tool(Icons.ALIGN_CENTER, "align_center", () -> editor.setAlignment(Alignment.CENTER))
						.showing(() -> editor.activeAlignment() == Alignment.CENTER),
				tool(Icons.ALIGN_RIGHT, "align_right", () -> editor.setAlignment(Alignment.RIGHT))
						.showing(() -> editor.activeAlignment() == Alignment.RIGHT),
				tool(Icons.ALIGN_JUSTIFY, "align_justify", () -> editor.setAlignment(Alignment.JUSTIFY))
						.showing(() -> editor.activeAlignment() == Alignment.JUSTIFY)));

		groups.add(List.of(
				tool(Icons.LIST_BULLET, "list_bullet", () -> editor.setList(ListStyle.BULLET))
						.showing(() -> editor.activeList() == ListStyle.BULLET || editor.activeList() == ListStyle.DASH),
				tool(Icons.LIST_NUMBER, "list_number", () -> editor.setList(ListStyle.NUMBER))
						.showing(() -> editor.activeList().numbered()),
				tool(Icons.INDENT_LESS, "indent_less", () -> editor.indent(-1)),
				tool(Icons.INDENT_MORE, "indent_more", () -> editor.indent(1))));

		// The link button only where a link can actually be kept. Outside creative the page goes as a
		// string and a string has nowhere to put one, so the button could only ever write the address
		// out as visible text – which is not what anybody pressing a link button is asking for.
		List<IconButton> inserts = new ArrayList<>();
		if (BookSender.canWriteRich()) {
			inserts.add(tool(Icons.LINK, "link", this::openLink));
		}
		inserts.addAll(List.of(
				tool(Icons.SYMBOL, "symbols", this::toggleSymbols).showing(() -> symbolsOpen),
				tool(Icons.TABLE, "table", () -> client.setScreen(new TableScreen(this, this::insertParagraphs))),
				tool(Icons.RULE, "rule", this::insertRule),
				tool(Icons.HYPHEN, "hyphenate", this::toggleHyphenation)
						.showing(() -> QuillConfig.get().hyphenate)));
		groups.add(inserts);

		groups.add(List.of(
				tool(Icons.LEADER, "leader", () -> {
					editor.insert(String.valueOf(Widths.LEADER));
					editor.touch();
				}),
				tool(Icons.FOOTNOTE, "footnote", this::addFootnote),
				tool(Icons.CONTENTS, "contents", this::insertContents),
				ornamentButton = tool(Icons.ORNAMENT, "ornament", this::toggleOrnaments)
						.showing(() -> ornamentPopup != null),
				tool(Icons.COLUMNS, "columns", () -> {
					editor.toTwoColumns();
					say(Text.translatable("roleplayersquill.columns.done"));
				}),
				tool(Icons.TEMPLATE, "template", () -> client.setScreen(new TemplateScreen(this, editor)))
						.onlyWhen(() -> editor.document().canAddPage())));

		groups.add(List.of(
				tool(Icons.FIND, "find", this::openFind),
				tool(Icons.SAVE, "save", this::save).showing(() -> editor.isDirty()),
				tool(Icons.REFLOW, "reflow", editor::reflow)
						.showing(() -> !editor.firstLineCutOff().isEmpty()),
				tool(Icons.UNDO, "undo", () -> editor.stepBack(false))
						.onlyWhen(() -> editor.document().canUndo()),
				tool(Icons.REDO, "redo", () -> editor.stepBack(true))
						.onlyWhen(() -> editor.document().canRedo())));

		// No button for "a page at the end": the book grows one by itself the moment anything is
		// written past the bottom of the last page, and a button for what already happens on its own
		// is a button that only ever gets pressed by mistake.
		groups.add(List.of(
				tool(Icons.PAGE_INSERT, "page_insert", editor::newPageAfter)
						.onlyWhen(() -> editor.document().canAddPage()),
				tool(Icons.PAGE_COPY, "page_copy", () -> QuillClipboard.putPage(editor.currentPage())),
				tool(Icons.PAGE_PASTE, "page_paste", this::pastePage).onlyWhen(QuillClipboard::hasPage),
				tool(Icons.CLEAR, "page_clear", editor::clearCurrentPage),
				tool(Icons.PAGE_REMOVE, "page_remove", editor::removePage),
				tool(Icons.PAGES, "pages", () -> client.setScreen(new PagesScreen(this, editor))),
				tool(Icons.HISTORY, "history", () -> client.setScreen(new HistoryScreen(this, editor))),
				// Beside the history on purpose: they are the same question asked of two different
				// shelves. The history is what this book said before; the shelf is what every other
				// book said, including the one that is now ash at the bottom of a ravine.
				tool(Icons.SHELF, "shelf", () -> client.setScreen(new ShelfScreen(this, editor)))));

		groups.add(List.of(
				tool(Icons.IMPORT, "import", this::importFile),
				tool(Icons.EXPORT, "export", this::exportFile),
				// Not greyed out when dictation is switched off. It was, and a grey button says only
				// that something is wrong, never what – the answer being a setting in another screen
				// that the player has no reason to suspect. Pressed, it says so in one line.
				tool(Icons.VOICE, "dictate", this::toggleDictation)
						.showing(Dictation::isRunning)
						.telling(() -> QuillConfig.get().voiceEnabled
								? Text.translatable("roleplayersquill.tool.dictate")
								: Text.translatable("roleplayersquill.tool.dictate").append("\n")
										.append(Text.translatable("roleplayersquill.voice.disabled")
												.formatted(Formatting.GRAY)))));

		return groups;
	}

	/**
	 * Marks the word the caret is at, and opens a note for it at the foot of the page.
	 *
	 * <p>The note is an ordinary paragraph under a rule, not a thing the book has to remember – a
	 * page is a flat string and would not remember it. Which means a footnote survives the book
	 * being closed and opened, and a reader without the mod sees exactly the same footnote.
	 */
	private void addFootnote() {
		List<Paragraph> page = editor.currentPage();
		int number = BookTools.footnotesOn(page) + 1;
		String mark = BookTools.superscript(number);

		editor.insert(mark);
		if (number == 1) {
			editor.appendParagraph(TableBuilder.rule());
		}
		Paragraph note = new Paragraph(mark + " ", QuillStyle.PLAIN.withItalic(true));
		editor.appendParagraph(note);
		editor.setCaret(editor.currentPage().size() - 1, note.length(), false);
		editor.touch();
	}

	/** Builds a contents page from the headings and puts it at the front of the book. */
	private void insertContents() {
		Text title = Text.translatable("roleplayersquill.contents.title");
		// Whatever contents are already there come out first. Building them twice should give one
		// contents page brought up to date, not two of them one after the other.
		editor.dropContents(title.getString());
		List<List<Paragraph>> contents = BookTools.contentsFor(editor.document().pages(), title);
		if (contents.isEmpty()) {
			say(Text.translatable("roleplayersquill.contents.none"));
			return;
		}
		editor.insertPagesAtFront(contents);
		say(Text.translatable("roleplayersquill.contents.built", contents.size()));
	}

	private void openFind() {
		closeColours();
		client.setScreen(new FindScreen(this, editor));
	}

	/** The find strip, under the book. Pressing the key again puts the cursor back in the box. */
	private void openFindBar() {
		closeColours();
		if (findBar == null) {
			findBar = new FindBar(editor, this::openFind, this::closeFindBar);
			clearAndInit();
		}
		if (findBar.box() != null) {
			setFocused(findBar.box());
			findBar.box().setFocused(true);
		}
	}

	private void closeFindBar() {
		findBar = null;
		clearAndInit();
	}

	/** Where the row of buttons ends, so a dialog can stand under the book instead of over it. */
	public int belowButtons() {
		return buttonsY + 20 + (recovery != null ? 22 : 0) + (findBar != null ? FindBar.height() + 6 : 0);
	}

	private IconButton tool(Icons.Icon icon, String key, Runnable action) {
		IconButton button = new IconButton(0, 0, icon,
				Text.translatable("roleplayersquill.tool." + key), () -> {
					action.run();
					editor.touch();
				});
		tools.add(button);
		return button;
	}

	/** Packs the groups into rows, never splitting one across two. */
	private void layoutTools(List<List<IconButton>> groups) {
		int step = IconButton.SIZE + 1;
		int gap = 5;
		int available = Math.max(step * 4, width - leftDock - 16);

		List<List<IconButton>> rows = new ArrayList<>();
		List<IconButton> row = new ArrayList<>();
		int used = 0;
		for (List<IconButton> group : groups) {
			int groupWidth = group.size() * step + gap;
			if (used + groupWidth > available && !row.isEmpty()) {
				rows.add(row);
				row = new ArrayList<>();
				used = 0;
			}
			row.addAll(group);
			// A gap after the group, marked by leaving a hole in the x positions later.
			used += groupWidth;
			row.add(null);
		}
		if (!row.isEmpty()) {
			rows.add(row);
		}
		// Each row ends with the gap marker that followed its last group; it would push the row a
		// few pixels off centre.
		for (List<IconButton> line : rows) {
			while (!line.isEmpty() && line.get(line.size() - 1) == null) {
				line.remove(line.size() - 1);
			}
		}

		int y = 4;
		for (List<IconButton> line : rows) {
			int lineWidth = 0;
			for (IconButton button : line) {
				lineWidth += button == null ? gap : step;
			}
			int x = leftDock + (width - leftDock - lineWidth) / 2;
			for (IconButton button : line) {
				if (button == null) {
					x += gap;
					continue;
				}
				button.setX(x);
				button.setY(y);
				addDrawableChild(button);
				x += step;
			}
			y += IconButton.SIZE + 2;
		}
		toolbarHeight = y + 2;
	}

	// ---- drawing ----------------------------------------------------------------------------------

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		renderInGameBackground(context);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Order matters and is the whole of the layering: the panels put their own boxes down, the
		// book goes on top of the world, and every widget is drawn last so that a palette hanging
		// over the page is over it rather than under it.
		if (symbolsOpen && symbolPanel != null) {
			symbolPanel.render(context, mouseX, mouseY);
		}

		var matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate((float) bookX, (float) bookY);
		matrices.scale(scale, scale);
		context.drawTexture(RenderPipelines.GUI_TEXTURED, BookScreen.BOOK_TEXTURE,
				0, 0, 0.0F, 0.0F, BOOK_SIZE, BOOK_SIZE, 256, 256);
		drawPageNumber(context);
		drawPage(context);
		drawArrows(context, mouseX, mouseY);
		matrices.popMatrix();

		if (correctPopup != null) {
			correctPopup.render(context, mouseX, mouseY);
		}
		if (stylePopup != null) {
			stylePopup.render(context, mouseX, mouseY);
		}
		if (ornamentPopup != null) {
			ornamentPopup.render(context, mouseX, mouseY);
		}
		if (colourPopup != null) {
			colourPopup.render(context, mouseX, mouseY);
		}
		if (findBar != null) {
			findBar.renderBehind(context);
		}
		super.render(context, mouseX, mouseY, delta);
		if (findBar != null) {
			findBar.renderTally(context, textRenderer);
		}
		// After the widgets rather than before them: this one stands over the page wherever the word
		// happens to be, so it is the one thing that must never end up behind a button.
		if (spellPopup != null) {
			spellPopup.render(context, mouseX, mouseY);
		}

		drawCounter(context);
		drawDictation(context);
		drawNotice(context);
		drawLinkTooltip(context, mouseX, mouseY);
	}

	/** What is under the cursor, if what is under the cursor can be clicked. */
	@Nullable
	private QuillStyle linkUnder(double mouseX, double mouseY) {
		if (!overPage(mouseX, mouseY)) {
			return null;
		}
		int[] at = locate(mouseX, mouseY);
		if (at == null) {
			return null;
		}
		Paragraph paragraph = editor.currentPage().get(at[0]);
		if (paragraph.isEmpty()) {
			return null;
		}
		QuillStyle style = paragraph.styleAt(Math.min(at[1], paragraph.length() - 1));
		return style.hasInteraction() ? style : null;
	}

	/**
	 * The address under the cursor, whether it was made a link or merely typed.
	 *
	 * <p>Books kept as a list of addresses are usually never signed – there is no reason to spend a
	 * book on a note to yourself – and in an unsigned book nothing is a link to the game, only text
	 * that happens to look like one. So the text is searched here the same way the reader searches
	 * it, and a click follows what it finds.
	 */
	@Nullable
	private String urlUnder(double mouseX, double mouseY) {
		if (!overPage(mouseX, mouseY)) {
			return null;
		}
		int[] at = locate(mouseX, mouseY);
		if (at == null) {
			return null;
		}
		Paragraph paragraph = editor.currentPage().get(at[0]);
		if (paragraph.isEmpty()) {
			return null;
		}
		int index = Math.min(at[1], paragraph.length() - 1);
		QuillStyle style = paragraph.styleAt(index);
		if (style.url() != null) {
			return style.url();
		}
		return com.glamardor.roleplayersquill.reader.LinkDetector.addressAt(paragraph.text(), index);
	}

	/**
	 * Says what a link is and how to follow it, while the book is being written.
	 *
	 * <p>A click follows it, because a book of addresses is a book people read rather than write in.
	 * Alt is the way back to the text: alt-click puts the caret where you pointed, which is what a
	 * plain click does everywhere on the page that is not an address.
	 */
	private void drawLinkTooltip(DrawContext context, int mouseX, int mouseY) {
		QuillStyle link = linkUnder(mouseX, mouseY);
		if (link == null) {
			String bare = urlUnder(mouseX, mouseY);
			if (bare != null) {
				context.drawTooltip(textRenderer,
						Text.translatable("roleplayersquill.link.follow", bare).formatted(Formatting.GRAY),
						mouseX, mouseY);
			}
			return;
		}
		MutableText tip = Text.empty();
		if (link.hover() != null) {
			tip.append(LegacyCodec.toText(link.hover())).append("\n");
		}
		if (link.url() != null) {
			tip.append(Text.translatable("roleplayersquill.link.follow", link.url()).formatted(Formatting.GRAY));
		} else if (link.command() != null) {
			tip.append(Text.translatable("roleplayersquill.link.runs", link.command()).formatted(Formatting.GRAY));
		} else if (link.page() > 0) {
			tip.append(Text.translatable("roleplayersquill.link.jumps", link.page()).formatted(Formatting.GRAY));
		}
		context.drawTooltip(textRenderer, tip, mouseX, mouseY);
	}

	/**
	 * The page number, inside the book's top margin, exactly where the vanilla reader puts it.
	 *
	 * <p>Inside the scaled matrix, so it shrinks and grows with the book rather than staying one
	 * size while the page under it changes.
	 */
	private void drawPageNumber(DrawContext context) {
		Text page = Text.translatable("book.pageIndicator", editor.page() + 1, editor.document().pageCount());
		// Black, like the vanilla reader draws it. A lighter ink was easier to tell from the text
		// and looked like a different book.
		context.drawText(textRenderer, page, BOOK_SIZE - 44 - textRenderer.getWidth(page), 16, INK, false);
	}

	/**
	 * The counter, above the book rather than on it.
	 *
	 * <p>It used to share the book's top margin with the page number. There is not room for both:
	 * the margin is 114 pixels of usable width, the two of them together are more than that, and on
	 * a short window where the book is drawn smaller than full size it was worse still, because the
	 * text did not shrink with the page it was written on.
	 */
	private void drawCounter(DrawContext context) {
		if (!QuillConfig.get().showCounter) {
			return;
		}
		int used = editor.cost();
		int lines = editor.contentLines();
		boolean over = used > QuillDocument.MAX_PAGE_CHARS || lines > Layout.PAGE_LINES;
		Formatting colour = over ? Formatting.RED
				: used > QuillDocument.MAX_PAGE_CHARS * 9 / 10 ? Formatting.GOLD : Formatting.GRAY;
		MutableText counter = Text.translatable("roleplayersquill.editor.counter",
				used, QuillDocument.MAX_PAGE_CHARS, lines, Layout.PAGE_LINES).formatted(colour);
		// The one number here that is about the writing rather than about the room left for it.
		counter.append(Text.translatable("roleplayersquill.editor.words",
				BookTools.wordsOn(editor.currentPage())).formatted(Formatting.DARK_GRAY));
		if (editor.isDirty()) {
			counter = Text.literal("• ").formatted(Formatting.GOLD).append(counter);
		}
		int centre = bookX + (int) (BOOK_SIZE * scale / 2);
		context.drawCenteredTextWithShadow(textRenderer, counter, centre, bookY - 11, 0xFFFFFFFF);
	}

	/**
	 * The arrows, drawn from the game's own sprites.
	 *
	 * <p>The same four textures {@code PageTurnWidget} uses, at the same size, in the same places –
	 * so they are the vanilla arrows, not a drawing of them. Drawn inside the book's matrix rather
	 * than added as widgets, which is the one thing a widget could not do: grow with the page when
	 * the page size setting is turned up.
	 */
	private void drawArrows(DrawContext context, int mouseX, int mouseY) {
		if (editor.page() > 0) {
			boolean hovered = overArrow(mouseX, mouseY, ARROW_PREVIOUS_X);
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,
					hovered ? PAGE_BACKWARD_HIGHLIGHTED : PAGE_BACKWARD,
					ARROW_PREVIOUS_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT);
		}
		if (editor.page() < editor.document().pageCount() - 1 || editor.document().canAddPage()) {
			boolean hovered = overArrow(mouseX, mouseY, ARROW_NEXT_X);
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,
					hovered ? PAGE_FORWARD_HIGHLIGHTED : PAGE_FORWARD,
					ARROW_NEXT_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT);
		}
	}

	private boolean overArrow(double mouseX, double mouseY, int localX) {
		float x = (float) ((mouseX - bookX) / scale);
		float y = (float) ((mouseY - bookY) / scale);
		return x >= localX && x <= localX + ARROW_WIDTH && y >= ARROW_Y && y <= ARROW_Y + ARROW_HEIGHT;
	}

	private void drawPage(DrawContext context) {
		List<Layout.LaidLine> lines = editor.lines();
		List<Paragraph> page = editor.currentPage();
		QuillConfig config = QuillConfig.get();

		if (config.showGuides) {
			context.drawBorder(TEXT_X - 1, TEXT_Y - 1, (int) Layout.PAGE_WIDTH + 2,
					Layout.PAGE_LINES * Layout.LINE_HEIGHT + 2, GUIDE);
			for (int i = 1; i < Layout.PAGE_LINES; i++) {
				context.fill(TEXT_X, TEXT_Y + i * Layout.LINE_HEIGHT,
						TEXT_X + (int) Layout.PAGE_WIDTH, TEXT_Y + i * Layout.LINE_HEIGHT + 1, GUIDE);
			}
		}

		PageEditor.Span selection = editor.selection();
		int caretLine = editor.lineIndexOfCaret();

		for (int i = 0; i < Math.min(lines.size(), Layout.PAGE_LINES); i++) {
			Layout.LaidLine line = lines.get(i);
			int y = TEXT_Y + i * Layout.LINE_HEIGHT;
			drawSelection(context, line, page.get(line.paragraph), selection, y);
			drawLine(context, line, page.get(line.paragraph), y);
			if (config.showGuides) {
				drawHeldBlanks(context, line, page.get(line.paragraph), y);
			}
			if (config.spellCheck) {
				drawMisspellings(context, line, page.get(line.paragraph), y);
			}
		}

		// Anything past the fourteenth line is not going to be in the book, for anybody. Saying so is
		// not enough on its own – say which words, because the line that falls off is at the bottom
		// of the page where nobody is looking, and a page can look finished while a name is missing
		// from it.
		String cut = editor.firstLineCutOff();
		if (!cut.isEmpty()) {
			context.drawText(textRenderer,
					Text.translatable("roleplayersquill.editor.overflow",
							textRenderer.trimToWidth(cut, (int) Layout.PAGE_WIDTH - 46)),
					TEXT_X, TEXT_Y + Layout.PAGE_LINES * Layout.LINE_HEIGHT + 1, 0xFFB03030, false);
		}

		if (blink / 6 % 2 == 0 && caretLine < Math.min(lines.size(), Layout.PAGE_LINES)) {
			Layout.LaidLine line = lines.get(caretLine);
			float x = TEXT_X + Layout.xOf(line, page.get(line.paragraph), editor.caret());
			int y = TEXT_Y + caretLine * Layout.LINE_HEIGHT;
			context.fill((int) x, y - 1, (int) x + 1, y + 9, INK);
		}
	}

	/**
	 * The dotted red line under a word nothing recognises.
	 *
	 * <p>Dotted rather than solid, and a row of single pixels rather than the game's own underline:
	 * an underline is part of the text's own formatting here, and a check that painted one would be
	 * saying something about the book rather than about the spelling.
	 *
	 * <p>A word can lie across a line break, and then each half is marked on its own line, which is
	 * the same arithmetic the search preview uses for the words it has found.
	 */
	private void drawMisspellings(DrawContext context, Layout.LaidLine line, Paragraph paragraph, int y) {
		List<Spelling.Word> words = Spelling.unknownIn(paragraph);
		if (words.isEmpty()) {
			return;
		}
		for (Spelling.Word word : words) {
			if (word.to() <= line.start || word.from() >= line.contentEnd) {
				continue;
			}
			int from = Math.max(word.from(), line.start);
			int to = Math.min(word.to(), line.contentEnd);
			int left = (int) (TEXT_X + Layout.xOf(line, paragraph, from));
			int right = (int) Math.ceil(TEXT_X + Layout.xOf(line, paragraph, to));
			for (int x = left; x < right; x += 2) {
				context.fill(x, y + 8, x + 1, y + 9, 0xFFC03030);
			}
		}
	}

	/**
	 * A dot under every blank that is holding two words together, while the guides are on.
	 *
	 * <p>Nothing about such a blank can be seen otherwise – that is the point of it – and a mark that
	 * cannot be seen is a mark that gets typed twice and deleted by accident. It goes on with the
	 * guides because that is the switch for exactly this: showing what the page is made of rather
	 * than what it looks like.
	 */
	private void drawHeldBlanks(DrawContext context, Layout.LaidLine line, Paragraph paragraph, int y) {
		for (int i = line.start; i < line.contentEnd; i++) {
			if (paragraph.charAt(i) != Widths.NOBREAK) {
				continue;
			}
			int x = (int) (TEXT_X + Layout.xOf(line, paragraph, i));
			context.fill(x + 1, y + 6, x + 3, y + 7, 0xFF9A7A3A);
		}
	}

	private void drawSelection(DrawContext context, Layout.LaidLine line, Paragraph paragraph,
			PageEditor.Span selection, int y) {
		if (selection.isEmpty()) {
			return;
		}
		if (line.paragraph < selection.fromParagraph() || line.paragraph > selection.toParagraph()) {
			return;
		}
		int from = line.paragraph == selection.fromParagraph() ? selection.fromIndex() : line.start;
		int to = line.paragraph == selection.toParagraph() ? selection.toIndex() : line.contentEnd;
		from = Math.max(from, line.start);
		to = Math.min(to, line.contentEnd);
		if (to < from) {
			return;
		}
		float x1 = TEXT_X + Layout.xOf(line, paragraph, from);
		float x2 = TEXT_X + Layout.xOf(line, paragraph, to);
		if (x2 - x1 < 1.0f) {
			// A selection that swallowed a line break still deserves to be visible.
			x2 = x1 + 2.0f;
		}
		context.fill((int) x1, y - 1, (int) Math.ceil(x2), y + 9, SELECTION);
	}

	private void drawLine(DrawContext context, Layout.LaidLine line, Paragraph paragraph, int y) {
		float x = TEXT_X + line.leftPad.width();
		if (line.frame.present()) {
			String bar = String.valueOf(line.frame.bar);
			context.drawText(textRenderer, bar, TEXT_X, y, INK, false);
			context.drawText(textRenderer, bar, (int) (TEXT_X + line.frame.barRight()), y, INK, false);
			x += line.frame.textLeft();
		}
		if (!line.marker.isEmpty()) {
			context.drawText(textRenderer,
					Text.literal(line.marker).setStyle(line.markerStyle.toVanilla(INK)), (int) x, y, INK, false);
			x += Widths.widthOf(line.marker, line.markerStyle.bold()) + line.markerPad.width();
		}

		StringBuilder run = new StringBuilder();
		QuillStyle runStyle = null;
		float runX = x;
		for (int i = line.start; i < line.contentEnd; i++) {
			char c = paragraph.charAt(i);
			QuillStyle style = paragraph.styleAt(i);
			if (i == line.leaderAt) {
				x = flush(context, run, runStyle, runX, y);
				runStyle = null;
				x = drawPad(context, line.leaderPad, style, x, y);
				if (line.leaderDots > 0) {
					String dots = ".".repeat(line.leaderDots);
					context.drawText(textRenderer, Text.literal(dots).setStyle(style.toVanilla(INK)),
							(int) x, y, INK, false);
					x += Widths.widthOf(dots, style.bold());
				}
				runX = x;
				continue;
			}
			Widths.Padding pad = c == ' ' ? line.padFor(i) : null;
			boolean widened = pad != null && (pad.count() != 1 || pad.bold() != 0);

			if (runStyle == null || !runStyle.equals(style) || widened) {
				x = flush(context, run, runStyle, runX, y);
				runStyle = widened ? null : style;
				runX = x;
			}
			if (widened) {
				x = drawPad(context, pad, style, x, y);
				runX = x;
				continue;
			}
			// The unbreakable blank is a space to the font, which has no glyph of its own for it and
			// would draw the box it draws for anything it does not know. Where it stands is shown by
			// the guides instead, along with everything else that is there and not visible.
			run.append(c == Widths.NOBREAK ? ' ' : c);
		}
		x = flush(context, run, runStyle, runX, y);

		if (line.hyphen) {
			QuillStyle style = paragraph.styleAt(Math.max(line.start, line.contentEnd - 1));
			context.drawText(textRenderer, Text.literal("-").setStyle(style.toVanilla(INK)), (int) x, y, INK, false);
		}
	}

	/**
	 * The blanks a widened gap is made of, drawn rather than stepped over.
	 *
	 * <p>They go onto the page wearing the style of the text around them – see the same condition in
	 * {@code LegacyCodec.encodeLine} – so an underline or a strikethrough runs straight through the
	 * gap in the finished book. Stepping over it here showed the writer a line broken in places where
	 * every reader sees it whole. Only those two decorations can be seen on a blank, so only they are
	 * worth a draw call; bold and colour change nothing about a space, and obfuscation is taken off
	 * the blanks by the encoder or the gap would fill with noise.
	 */
	private float drawPad(DrawContext context, Widths.Padding pad, QuillStyle style, float x, int y) {
		QuillStyle flat = style.withObfuscated(false).withBold(false);
		if (!flat.underlined() && !flat.strikethrough()) {
			return x + pad.width();
		}
		int plain = Math.max(0, pad.count() - pad.bold());
		if (plain > 0) {
			context.drawText(textRenderer, Text.literal(" ".repeat(plain)).setStyle(flat.toVanilla(INK & 0xFFFFFF)),
					(int) x, y, INK, false);
			x += plain * Widths.space();
		}
		if (pad.bold() > 0) {
			QuillStyle bold = flat.withBold(true);
			context.drawText(textRenderer,
					Text.literal(" ".repeat(pad.bold())).setStyle(bold.toVanilla(INK & 0xFFFFFF)),
					(int) x, y, INK, false);
			x += pad.bold() * Widths.boldSpace();
		}
		return x;
	}

	private float flush(DrawContext context, StringBuilder run, @Nullable QuillStyle style, float x, int y) {
		if (run.isEmpty()) {
			return x;
		}
		QuillStyle applied = style == null ? QuillStyle.PLAIN : style;
		boolean link = QuillConfig.get().highlightLinks && applied.hasInteraction();
		int colour = link ? LINK_INK : applied.color() == QuillStyle.INHERIT ? INK : 0xFF000000 | applied.color();
		Style vanilla = applied.toVanilla(colour & 0xFFFFFF);
		if (link) {
			vanilla = vanilla.withUnderline(true).withColor(LINK_INK & 0xFFFFFF);
		}
		String text = run.toString();
		context.drawText(textRenderer, Text.literal(text).setStyle(vanilla), (int) x, y, colour, false);
		run.setLength(0);
		return x + Widths.widthOf(text, applied.bold());
	}

	private void drawDictation(DrawContext context) {
		// The download bar first and on its own terms: it is the only thing on this screen that can
		// take five minutes, and it has to be visible whatever else is going on.
		int barHeight = ProgressOverlay.render(context, textRenderer, width / 2, height - 6);

		if (Dictation.state() == Dictation.State.OFF) {
			return;
		}
		int y = height - 22 - barHeight;
		String heard = Dictation.state() == Dictation.State.LISTENING ? Dictation.visible() : "";
		Text status = heard.isEmpty() ? Dictation.status() : Text.literal(heard).formatted(Formatting.ITALIC);
		int textWidth = textRenderer.getWidth(status);
		context.fill(width / 2 - textWidth / 2 - 6, y - 3, width / 2 + textWidth / 2 + 6, y + 12, 0xC0000000);
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, y,
				Dictation.state() == Dictation.State.ERROR ? 0xFFFF7070 : 0xFFE0E0E0);
		if (Dictation.state() == Dictation.State.LISTENING) {
			int meter = Math.round(Dictation.level() * 40);
			context.fill(width / 2 - 20, y - 7, width / 2 - 20 + meter, y - 4, 0xFF60C060);
		}
	}

	private void drawNotice(DrawContext context) {
		if (notice == null || System.currentTimeMillis() > noticeUntil) {
			return;
		}
		int textWidth = textRenderer.getWidth(notice);
		int y = toolbarHeight - 2;
		context.fill(width / 2 - textWidth / 2 - 4, y, width / 2 + textWidth / 2 + 4, y + 12, 0xC0202020);
		context.drawCenteredTextWithShadow(textRenderer, notice, width / 2, y + 2, 0xFFE8D8A0);
	}

	/**
	 * Shows a line of explanation for a few seconds.
	 *
	 * <p>Timed off the clock rather than off the caret counter. The caret counter is reset on every
	 * keystroke, so a notice put up while somebody was typing was pushed back by every letter they
	 * typed after it, and a notice about hyphenation – which is exactly the sort you read and then
	 * carry on writing – never went away at all.
	 */
	private void say(Text message) {
		say(message, 4000L);
	}

	private void say(Text message, long millis) {
		notice = message;
		noticeUntil = System.currentTimeMillis() + millis;
	}

	/**
	 * Says so when the font this page is being measured against is not the one it will be read in.
	 *
	 * <p>Every space this mod writes is arithmetic over glyph advances, and the advances come from
	 * the font the client has loaded. Turn the unicode font on and every letter changes width at
	 * once: the page still looks laid out here, because here it is measured the same way it was
	 * written, and lands crooked for everybody reading it with the ordinary font. Worse, a line laid
	 * out to the full 114 pixels under one font can be wider than that under another, and then the
	 * game breaks it in two and the last line of the page falls off the bottom.
	 *
	 * <p>Once per screen, not once per resize, and only where it is true.
	 */
	private void warnAboutFont() {
		if (fontWarned || client == null) {
			return;
		}
		fontWarned = true;
		if (client.options.getForceUnicodeFont().getValue()) {
			say(Text.translatable("roleplayersquill.editor.unicodefont"), 10000L);
		}
	}

	/**
	 * Says so when the book was written before this mod could tell black from nothing at all.
	 *
	 * <p>Such a book reads perfectly – on parchment the two are the same ink – and falls apart the
	 * moment the server tears a page out of it, where black lands on a dark tooltip and the sentence
	 * is gone. Writing the book back mends it, and writing the book back is the one thing this
	 * editor will not do on its own. So the book is marked as having something unsaved in it, which
	 * is true, and the writer is told what that something is.
	 *
	 * <p>Asked of the strings the book arrived as, and not of the document. A page knows what it
	 * came in as only where reading it and writing it again land on the same page exactly – which is
	 * the common case and not the certain one, and a book whose pages all fell just short of it said
	 * nothing at all while quietly needing mending on every one of them.
	 *
	 * <p>And asked properly: not "is there ink on this page" but "would writing it again take some
	 * off". Some cannot be taken off – a paragraph the game breaks up in the middle of something
	 * bold has nowhere to put a {@code §r} – and a book that cannot be mended must not be offered
	 * for mending, or it says the same thing every time it is opened for the rest of its life.
	 */
	private void offerToMend() {
		if (mendOffered) {
			return;
		}
		mendOffered = true;
		for (String page : original) {
			int ink = LegacyCodec.blackInk(page);
			if (ink == 0) {
				continue;
			}
			List<Paragraph> read = LegacyCodec.decode(page);
			String again = LegacyCodec.encode(read, Layout.lay(read, editor.layoutOptions()));
			if (LegacyCodec.blackInk(again) < ink) {
				editor.touch();
				say(Text.translatable("roleplayersquill.editor.oldink"), 10000L);
				return;
			}
		}
	}

	@Override
	public void tick() {
		blink++;
		keepDraft(false);
		if (colourButton != null) {
			int colour = editor.activeStyle().color();
			colourButton.setColour(colour == QuillStyle.INHERIT ? 0x303030 : colour);
		}
	}

	// ---- the mouse --------------------------------------------------------------------------------

	/** Where in the document a point on the screen is. */
	@Nullable
	private int[] locate(double mouseX, double mouseY) {
		float localX = (float) ((mouseX - bookX) / scale) - TEXT_X;
		float localY = (float) ((mouseY - bookY) / scale) - TEXT_Y;
		List<Layout.LaidLine> lines = editor.lines();
		if (lines.isEmpty()) {
			return null;
		}
		int index = MathHelper.clamp((int) Math.floor(localY / Layout.LINE_HEIGHT), 0,
				Math.min(lines.size(), Layout.PAGE_LINES) - 1);
		Layout.LaidLine line = lines.get(index);
		int at = Layout.indexAt(line, editor.currentPage().get(line.paragraph), localX);
		return new int[] { line.paragraph, at };
	}

	private boolean overPage(double mouseX, double mouseY) {
		float localX = (float) ((mouseX - bookX) / scale);
		float localY = (float) ((mouseY - bookY) / scale);
		return localX >= TEXT_X - 4 && localX <= TEXT_X + Layout.PAGE_WIDTH + 4
				&& localY >= TEXT_Y - 2 && localY <= TEXT_Y + Layout.PAGE_LINES * Layout.LINE_HEIGHT + 2;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// The spelling menu stands over the page rather than under a button, so it answers first and
		// a click anywhere else puts it away – which is what a menu opened by a right click does.
		if (spellPopup != null) {
			if (spellPopup.contains(mouseX, mouseY)) {
				return spellPopup.pickAt(mouseX, mouseY);
			}
			closeSpell();
			return true;
		}
		if (button == 1 && openSpellMenu(mouseX, mouseY)) {
			return true;
		}
		// The palette answers first while it is down, except over the button that opened it – there a
		// click has to reach the button, or closing and reopening would cancel each other out.
		if (ornamentPopup != null && !(ornamentButton != null && ornamentButton.isMouseOver(mouseX, mouseY))) {
			if (ornamentPopup.contains(mouseX, mouseY)) {
				return ornamentPopup.pickAt(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
			}
			closeOrnaments();
			return true;
		}
		if (stylePopup != null && !(styleButton != null && styleButton.isMouseOver(mouseX, mouseY))) {
			if (stylePopup.contains(mouseX, mouseY)) {
				return stylePopup.pickAt(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
			}
			closeStyles();
			return true;
		}
		if (correctPopup != null && !(correctButton != null && correctButton.isMouseOver(mouseX, mouseY))) {
			if (correctPopup.contains(mouseX, mouseY)) {
				return correctPopup.pickAt(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
			}
			closeCorrect();
			return true;
		}
		if (colourPopup != null && !(colourButton != null && colourButton.isMouseOver(mouseX, mouseY))) {
			if (colourPopup.contains(mouseX, mouseY)) {
				// A swatch, or else the box and the buttons, which are widgets like any other.
				return colourPopup.pickAt(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
			}
			// Anywhere else puts it away, the way a menu behaves.
			closeColours();
			return true;
		}
		if (symbolsOpen && symbolPanel != null && symbolPanel.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		// A click on an address follows it, signed book or not. Holding alt says the click was meant
		// for the text – that is how you get the caret inside an address in order to edit it.
		if (button == 0 && !hasAltDown()) {
			String url = urlUnder(mouseX, mouseY);
			if (url != null) {
				net.minecraft.client.gui.screen.ConfirmLinkScreen.open(this, url);
				return true;
			}
		}
		if (button == 0) {
			if (editor.page() > 0 && overArrow(mouseX, mouseY, ARROW_PREVIOUS_X)) {
				turnPage(-1);
				playClick();
				return true;
			}
			if (overArrow(mouseX, mouseY, ARROW_NEXT_X)) {
				turnPage(1);
				playClick();
				return true;
			}
		}
		if (button != 0 || !overPage(mouseX, mouseY)) {
			return false;
		}
		int[] at = locate(mouseX, mouseY);
		if (at == null) {
			return false;
		}

		long now = System.currentTimeMillis();
		clickCount = now - lastClickTime < 250L ? clickCount + 1 : 1;
		lastClickTime = now;

		editor.setCaret(at[0], at[1], hasShiftDown());
		if (clickCount == 2) {
			editor.selectWord();
		} else if (clickCount >= 3) {
			editor.selectLine();
		}
		dragging = clickCount == 1;
		blink = 0;
		// A word or a line picked out in one click, or a selection extended with shift, is finished
		// here and now – there is no release to wait for.
		paintIfArmed();
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging && button == 0) {
			int[] at = locate(mouseX, mouseY);
			if (at != null) {
				editor.setCaret(at[0], at[1], true);
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		boolean wasDragging = dragging;
		dragging = false;
		if (wasDragging && button == 0) {
			if (brushArmed && editor.brush() != null && !editor.hasSelection()) {
				// A click and no drag, with the brush waiting: the word clicked on is what was meant,
				// the same as clicking a word with the painter anywhere else.
				editor.selectWord();
			}
			// The drag is over, so whatever it swept out is the selection the brush was told to wait for.
			paintIfArmed();
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (symbolsOpen && symbolPanel != null && symbolPanel.contains(mouseX, mouseY)) {
			return symbolPanel.mouseScrolled(vertical);
		}
		if (overPage(mouseX, mouseY)) {
			// Wheel away from you turns forward, the way a scroll bar and a book both go.
			turnPage(vertical > 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	private void playClick() {
		client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
				net.minecraft.sound.SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
	}

	private void turnPage(int delta) {
		int target = editor.page() + delta;
		if (target < 0) {
			return;
		}
		if (target >= editor.document().pageCount()) {
			if (!editor.document().canAddPage()) {
				return;
			}
			// Turning past the last page is asking for a page after it, not for a blank one where the
			// last page stands.
			editor.appendPage();
			return;
		}
		editor.setPage(target);
	}

	// ---- the keyboard -----------------------------------------------------------------------------

	@Override
	public boolean charTyped(char chr, int modifiers) {
		// While the browser's search box has the keyboard, typing belongs to it and not to the book.
		if (typingInSearch()) {
			return super.charTyped(chr, modifiers);
		}
		// The spelling menu points at a word by where it sits in the paragraph, and a paragraph being
		// typed in is a paragraph whose words are moving. It goes away at the first keystroke.
		if (spellPopup != null) {
			closeSpell();
		}
		if (!StringHelperCompat.isValid(chr)) {
			return false;
		}
		editor.type(chr);
		blink = 0;
		return true;
	}

	private boolean typingInSearch() {
		if (colourPopup != null && colourPopup.typing(getFocused())) {
			return true;
		}
		if (findBar != null && findBar.box() != null && getFocused() == findBar.box()) {
			return true;
		}
		return symbolsOpen && symbolPanel != null && symbolPanel.searching(getFocused());
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		blink = 0;
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && spellPopup != null) {
			closeSpell();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_F7) {
			// What F7 does in a word processor, and the one key nothing else in this editor wants.
			// With shift it walks the book instead of switching the check on and off.
			if (Screen.hasShiftDown()) {
				nextMisspelling();
			} else {
				toggleSpell();
			}
			return true;
		}
		if (spellPopup != null && (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE
				|| keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			closeSpell();
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && ornamentPopup != null) {
			closeOrnaments();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && stylePopup != null) {
			closeStyles();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && correctPopup != null) {
			closeCorrect();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && colourPopup != null) {
			closeColours();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && symbolsOpen) {
			toggleSymbols();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && findBar != null) {
			closeFindBar();
			return true;
		}
		// Enter in the find box walks the matches, the way it does in every search box: forwards,
		// or backwards with shift. Without this it would fall through and put a line break in the
		// book somebody is searching.
		if (findBar != null && findBar.box() != null && getFocused() == findBar.box()
				&& (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			findBar.step(!Screen.hasShiftDown());
			return true;
		}
		if (typingInSearch() && !Screen.hasControlDown()) {
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		boolean control = Screen.hasControlDown();
		boolean shift = Screen.hasShiftDown();

		if (control && handleShortcut(keyCode, shift)) {
			return true;
		}

		switch (keyCode) {
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if (control) {
					editor.newPageAfter();
				} else {
					editor.newParagraph();
				}
				return true;
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				editor.backspace(control);
				return true;
			}
			case GLFW.GLFW_KEY_DELETE -> {
				editor.deleteForward(control);
				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> {
				editor.moveLeft(shift, control);
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				editor.moveRight(shift, control);
				return true;
			}
			case GLFW.GLFW_KEY_UP -> {
				editor.moveVertically(-1, shift);
				return true;
			}
			case GLFW.GLFW_KEY_DOWN -> {
				editor.moveVertically(1, shift);
				return true;
			}
			case GLFW.GLFW_KEY_HOME -> {
				editor.moveToLineStart(shift);
				return true;
			}
			case GLFW.GLFW_KEY_END -> {
				editor.moveToLineEnd(shift);
				return true;
			}
			case GLFW.GLFW_KEY_PAGE_UP -> {
				turnPage(-1);
				return true;
			}
			case GLFW.GLFW_KEY_PAGE_DOWN -> {
				turnPage(1);
				return true;
			}
			case GLFW.GLFW_KEY_TAB -> {
				editor.indent(shift ? -1 : 1);
				return true;
			}
			default -> {
			}
		}

		if (RoleplayersQuillClient.dictateKey != null
				&& RoleplayersQuillClient.dictateKey.matchesKey(keyCode, scanCode)) {
			toggleDictation();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		// Only once it is actually listening. Letting go of the key while the engine is still being
		// fetched used to cancel the fetch, which is a hundred megabytes thrown away for holding a
		// key half a second.
		if (QuillConfig.get().voicePushToTalk && Dictation.state() == Dictation.State.LISTENING
				&& RoleplayersQuillClient.dictateKey != null
				&& RoleplayersQuillClient.dictateKey.matchesKey(keyCode, scanCode)) {
			Dictation.stop();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	private boolean handleShortcut(int keyCode, boolean shift) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_B -> {
				editor.toggle(QuillStyle::bold, QuillStyle::withBold);
				return true;
			}
			case GLFW.GLFW_KEY_I -> {
				editor.toggle(QuillStyle::italic, QuillStyle::withItalic);
				return true;
			}
			case GLFW.GLFW_KEY_U -> {
				editor.toggle(QuillStyle::underlined, QuillStyle::withUnderlined);
				return true;
			}
			case GLFW.GLFW_KEY_S -> {
				if (shift) {
					editor.toggle(QuillStyle::strikethrough, QuillStyle::withStrikethrough);
				} else {
					save();
				}
				return true;
			}
			case GLFW.GLFW_KEY_O -> {
				if (shift) {
					editor.toggle(QuillStyle::obfuscated, QuillStyle::withObfuscated);
					return true;
				}
				return false;
			}
			case GLFW.GLFW_KEY_L -> {
				editor.setAlignment(Alignment.LEFT);
				return true;
			}
			case GLFW.GLFW_KEY_E -> {
				editor.setAlignment(Alignment.CENTER);
				return true;
			}
			case GLFW.GLFW_KEY_R -> {
				editor.setAlignment(Alignment.RIGHT);
				return true;
			}
			case GLFW.GLFW_KEY_J -> {
				editor.setAlignment(Alignment.JUSTIFY);
				return true;
			}
			case GLFW.GLFW_KEY_Z -> {
				editor.stepBack(shift);
				return true;
			}
			case GLFW.GLFW_KEY_Y -> {
				editor.stepBack(true);
				return true;
			}
			case GLFW.GLFW_KEY_A -> {
				editor.selectAll();
				return true;
			}
			case GLFW.GLFW_KEY_C -> {
				if (shift) {
					brush();
				} else {
					copy(false);
				}
				return true;
			}
			case GLFW.GLFW_KEY_X -> {
				copy(true);
				return true;
			}
			case GLFW.GLFW_KEY_V -> {
				if (shift) {
					applyBrush();
				} else {
					paste();
				}
				return true;
			}
			case GLFW.GLFW_KEY_F -> {
				// The strip, not the window: looking for a word is the common case by a long way,
				// and it does not deserve a form standing over the page it is looking through. The
				// window with replacing in it is one button along.
				openFindBar();
				return true;
			}
			case GLFW.GLFW_KEY_K -> {
				openLink();
				return true;
			}
			case GLFW.GLFW_KEY_G -> {
				toggleSymbols();
				return true;
			}
			case GLFW.GLFW_KEY_T -> {
				client.setScreen(new TableScreen(this, this::insertParagraphs));
				return true;
			}
			case GLFW.GLFW_KEY_H -> {
				toggleHyphenation();
				return true;
			}
			case GLFW.GLFW_KEY_D -> {
				editor.duplicatePage();
				return true;
			}
			case GLFW.GLFW_KEY_M -> {
				toggleDictation();
				return true;
			}
			case GLFW.GLFW_KEY_SPACE -> {
				if (!shift) {
					return false;
				}
				// The same keys a word processor uses for it, and the same idea: a blank the line will
				// not be broken at. Said out loud once, because nothing about it can be seen.
				editor.insert(String.valueOf(Widths.NOBREAK));
				if (!heldBlankExplained) {
					heldBlankExplained = true;
					say(Text.translatable("roleplayersquill.nobreak.done"));
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	// ---- what the buttons do -------------------------------------------------------------------------

	private void copy(boolean cut) {
		if (!editor.hasSelection()) {
			return;
		}
		String plain = editor.selectedText(false);
		QuillClipboard.put(editor.selectedParagraphs(), plain);
		client.keyboard.setClipboard(plain);
		if (cut) {
			editor.deleteSelection();
		}
	}

	private void paste() {
		String text = client.keyboard.getClipboard();
		if (text == null || text.isEmpty()) {
			return;
		}
		List<Paragraph> styled = QuillClipboard.matching(text);
		if (styled != null) {
			editor.insertParagraphs(styled);
			return;
		}
		QuillConfig config = QuillConfig.get();
		List<Paragraph> incoming = Paginator.parse(text, false, editor.activeStyle().withoutInteraction(),
				editor.activeAlignment());
		if (incoming.size() == 1) {
			editor.insert(incoming.get(0).text());
			return;
		}
		if (!config.autoPasteMultiPage) {
			editor.insert(text.replace("\n", " "));
			return;
		}
		int pages = Paginator.countPages(incoming, config.paginatorOptions());
		if (pages > 1 && config.confirmBigPaste) {
			confirmThen(Text.translatable("roleplayersquill.paste.confirm"),
					Text.translatable("roleplayersquill.paste.confirm.detail", pages),
					() -> editor.insertParagraphs(incoming));
			return;
		}
		editor.insertParagraphs(incoming);
	}

	private void insertParagraphs(List<Paragraph> paragraphs) {
		editor.insertParagraphs(paragraphs);
		editor.touch();
	}

	/**
	 * Puts a copied page in place of the one being looked at.
	 *
	 * <p>In place of, not after: copying a page and pasting it is how a page is replaced with
	 * another, and a paste that added a page instead left the old one still there to be deleted by
	 * hand. A blank page to write on is what the other button is for.
	 */
	private void pastePage() {
		if (!QuillClipboard.hasPage()) {
			return;
		}
		editor.document().mark();
		editor.document().pages().set(editor.page(), QuillClipboard.takePage());
		editor.setPage(editor.page());
		editor.touch();
	}

	/**
	 * Picks the formatting up, and arms the next selection to receive it.
	 *
	 * <p>The way a format painter works everywhere else: press it once, then select something, and
	 * the something is painted. Pressing it twice used to be the way, and the second press is
	 * exactly the step nobody remembers – by then you have selected the words and are waiting for
	 * something to happen.
	 *
	 * <p>Pressing it again while it is held puts it down without painting anything.
	 */
	private void brush() {
		if (editor.brush() != null) {
			editor.setBrush(null);
			brushArmed = false;
			say(Text.translatable("roleplayersquill.brush.dropped"));
			return;
		}
		editor.setBrush(editor.activeStyle().lookOnly());
		brushArmed = true;
		brushFrom = editor.selection();
		brushPage = editor.page();
		say(Text.translatable("roleplayersquill.brush.picked"));
	}

	/**
	 * Paints the selection the player has just made, if the brush is waiting for one.
	 *
	 * <p>Not the selection it was picked up from: that one is what the style came out of, and
	 * painting it with itself would look like the brush had done nothing.
	 */
	private void paintIfArmed() {
		if (!brushArmed || editor.brush() == null || !editor.hasSelection()) {
			return;
		}
		if (editor.page() == brushPage && editor.selection().equals(brushFrom)) {
			return;
		}
		applyBrush();
	}

	private void applyBrush() {
		QuillStyle held = editor.brush();
		if (held == null) {
			say(Text.translatable("roleplayersquill.brush.empty"));
			return;
		}
		if (!editor.hasSelection()) {
			say(Text.translatable("roleplayersquill.brush.select"));
			return;
		}
		editor.restyle(style -> new QuillStyle(held.bold(), held.italic(), held.underlined(),
				held.strikethrough(), held.obfuscated(), held.color(),
				style.url(), style.hover(), style.command(), style.copy(), style.page()));
		editor.setBrush(null);
		brushArmed = false;
	}

	private void insertRule() {
		// An underlined run of blanks is a horizontal rule that costs nothing but spaces, and every
		// reader sees it whether or not they have this mod. The same one the table builder draws.
		Paragraph rule = TableBuilder.rule();
		rule.setAlignment(Alignment.LEFT);
		insertParagraphs(List.of(rule));
	}

	/**
	 * Turns the spelling check on and off, and fetches what it needs the first time.
	 *
	 * <p>Nothing is downloaded behind anybody's back: the first press with no dictionary installed is
	 * the asking, and it says how many megabytes it is about to spend. A press with the lists already
	 * here is just a switch.
	 */
	private void toggleSpell() {
		QuillConfig config = QuillConfig.get();
		config.spellCheck = !config.spellCheck;
		config.save();
		spellPopup = null;
		Spelling.forget();
		if (!config.spellCheck) {
			say(Text.translatable("roleplayersquill.spell.off"));
			return;
		}
		if (Spelling.anyInstalled()) {
			Spelling.load();
			say(Text.translatable("roleplayersquill.spell.on"));
			return;
		}
		if (!config.spellAutoDownload) {
			say(Text.translatable("roleplayersquill.spell.needed", dictionarySize()), 8000L);
			return;
		}
		if (DictionaryDownload.startOnce(Spelling.missing(), this::clearAndInit)) {
			say(Text.translatable("roleplayersquill.spell.fetching", dictionarySize()), 8000L);
		}
	}

	/**
	 * What the spelling button says on hover: how the book stands, rather than how this page does.
	 *
	 * <p>The count belongs here and nowhere else. A number standing over the page would be a number
	 * in the way of the writing, and the one moment anybody wants it is the moment they reach for the
	 * button – at which point it is worth three lines: how many, how to walk them, and how to answer
	 * one.
	 */
	private Text spellTooltip() {
		MutableText tip = Text.translatable("roleplayersquill.tool.spell");
		if (!Spelling.anyInstalled()) {
			return tip.append("\n").append(Text.translatable("roleplayersquill.spell.needed",
					dictionarySize()).formatted(Formatting.GRAY));
		}
		if (!QuillConfig.get().spellCheck) {
			return tip;
		}
		int found = Spelling.countIn(editor.document().pages());
		tip.append("\n").append(found == 0
				? Text.translatable("roleplayersquill.spell.clean").formatted(Formatting.GREEN)
				: Text.translatable("roleplayersquill.spell.found", found,
						editor.document().pageCount()).formatted(Formatting.GOLD));
		if (found > 0) {
			tip.append("\n").append(Text.translatable("roleplayersquill.spell.walk").formatted(Formatting.GRAY));
		}
		return tip;
	}

	/**
	 * Goes to the next word in the book that nothing recognises, wherever it is.
	 *
	 * <p>Underlining happens on the page being drawn, which leaves every other page of a twenty-page
	 * book unwatched. This is the way through them: forward from the caret, on past the end of the
	 * page, round to the beginning when the book runs out – the same walk the search does, because it
	 * is the same question asked of a different list.
	 */
	private void nextMisspelling() {
		if (!QuillConfig.get().spellCheck || !Spelling.ready()) {
			say(Text.translatable("roleplayersquill.spell.off"));
			return;
		}
		List<List<Paragraph>> pages = editor.document().pages();
		int fromPage = editor.page();
		int fromParagraph = editor.paragraphIndex();
		int after = Math.max(editor.caret(), editor.selection().toIndex());

		for (int step = 0; step <= pages.size(); step++) {
			int index = (fromPage + step) % pages.size();
			List<Paragraph> page = pages.get(index);
			for (int p = 0; p < page.size(); p++) {
				// On the page the caret is on, the first time round, start where the caret is.
				boolean behind = step == 0 && (p < fromParagraph);
				if (behind) {
					continue;
				}
				for (Spelling.Word word : Spelling.unknownIn(page.get(p))) {
					if (step == 0 && p == fromParagraph && word.from() < after) {
						continue;
					}
					show(index, p, word);
					return;
				}
			}
			// Coming round to the page we started on, take it from the top this time.
			if (step == pages.size() - 1) {
				fromParagraph = 0;
				after = -1;
			}
		}
		say(Text.translatable("roleplayersquill.spell.clean"));
	}

	/** Turns to a word and picks it out, so that the next keystroke replaces it. */
	private void show(int page, int paragraph, Spelling.Word word) {
		if (page != editor.page()) {
			editor.setPage(page);
		}
		editor.setCaret(paragraph, word.from(), false);
		editor.setCaret(paragraph, word.to(), true);
		blink = 0;
		say(Text.literal(word.text()).formatted(Formatting.GOLD));
	}

	/** How big the download would be, for saying so before it starts. */
	private int dictionarySize() {
		int total = 0;
		for (Spelling.Tongue tongue : Spelling.missing()) {
			total += tongue.megabytes();
		}
		return total;
	}

	/**
	 * Opens the menu over a misspelled word.
	 *
	 * <p>On the right button, where every word processor keeps it, and only over a word that is
	 * actually marked – a right click anywhere else is left to whatever else wants it.
	 *
	 * @return whether there was a word there
	 */
	private boolean openSpellMenu(double mouseX, double mouseY) {
		if (!QuillConfig.get().spellCheck || !overPage(mouseX, mouseY)) {
			return false;
		}
		int[] at = locate(mouseX, mouseY);
		if (at == null) {
			return false;
		}
		Paragraph paragraph = editor.currentPage().get(at[0]);
		Spelling.Word word = null;
		for (Spelling.Word candidate : Spelling.unknownIn(paragraph)) {
			if (at[1] >= candidate.from() && at[1] <= candidate.to()) {
				word = candidate;
				break;
			}
		}
		if (word == null) {
			return false;
		}
		int index = at[0];
		Spelling.Word chosen = word;
		spellPopup = new SpellPopup(word.text(), Spelling.suggest(word.text()),
				replacement -> {
					editor.replaceIn(index, chosen.from(), chosen.to(), replacement);
					closeSpell();
				},
				() -> {
					Spelling.learn(chosen.text());
					closeSpell();
				},
				() -> {
					Spelling.ignore(chosen.text());
					closeSpell();
				});
		spellX = (int) mouseX;
		spellY = (int) mouseY + 4;
		clearAndInit();
		return true;
	}

	private void closeSpell() {
		spellPopup = null;
		clearAndInit();
	}

	private void toggleHyphenation() {
		QuillConfig config = QuillConfig.get();
		config.hyphenate = !config.hyphenate;
		config.save();
		editor.invalidate();
		say(Text.translatable(config.hyphenate
				? "roleplayersquill.hyphenate.on" : "roleplayersquill.hyphenate.off"));
	}

	private void openLink() {
		client.setScreen(new LinkScreen(this, editor));
	}

	/** Opens and closes the browser beside the book. Everything else shifts over to make room. */
	private void toggleSymbols() {
		symbolsOpen = !symbolsOpen;
		clearAndInit();
	}

	/** Drops the palette down under its button, or puts it away again. */
	private void toggleColours() {
		colourPopup = colourPopup == null
				? new ColourPopup(colour -> editor.restyle(style -> style.withColor(colour)), this::closeColours)
				: null;
		clearAndInit();
	}

	private void closeColours() {
		colourPopup = null;
		clearAndInit();
	}

	/**
	 * Turns correcting on or off, and shows the rules while it is on.
	 *
	 * <p>One button for both because they are one thought: the moment somebody wants the switch,
	 * what they usually want is the one rule that just got in the way, and that is on the list this
	 * opens. Pressing it again puts the list away and leaves correcting as it was; the tick on the
	 * button is the switch, and it is the first line of the list.
	 */
	private void toggleCorrect() {
		if (correctPopup != null) {
			correctPopup = null;
			clearAndInit();
			return;
		}
		QuillConfig config = QuillConfig.get();
		config.autoCorrect = !config.autoCorrect;
		config.save();
		say(Text.translatable(config.autoCorrect
				? "roleplayersquill.correct.on" : "roleplayersquill.correct.off"));
		if (config.autoCorrect) {
			correctPopup = new CorrectPopup(this::clearAndInit);
		}
		clearAndInit();
	}

	private void closeCorrect() {
		correctPopup = null;
		clearAndInit();
	}

	private void toggleOrnaments() {
		ornamentPopup = ornamentPopup == null
				? new OrnamentPopup(divider -> {
					editor.insertOrnament(divider.build());
					closeOrnaments();
				}, style -> {
					editor.frameCurrentPage(style);
					say(Text.translatable(style.present()
							? "roleplayersquill.ornament.framed" : "roleplayersquill.ornament.unframed"));
					closeOrnaments();
				})
				: null;
		clearAndInit();
	}

	private void closeOrnaments() {
		ornamentPopup = null;
		clearAndInit();
	}

	private void toggleStyles() {
		stylePopup = stylePopup == null
				? new StylePopup(style -> {
					editor.setParagraphStyle(style);
					closeStyles();
				}, editor::paragraphStyle)
				: null;
		clearAndInit();
	}

	private void closeStyles() {
		stylePopup = null;
		clearAndInit();
	}

	private void toggleDictation() {
		QuillConfig config = QuillConfig.get();
		if (!config.voiceEnabled) {
			say(Text.translatable("roleplayersquill.voice.disabled"));
			return;
		}
		Dictation.toggle(text -> {
			editor.insert(editor.caret() > 0 && !text.isEmpty() ? " " + text : text);
			editor.touch();
		});
	}

	// ---- files ---------------------------------------------------------------------------------------

	private void importFile() {
		FileDialogs.open(Text.translatable("roleplayersquill.import.title").getString(),
				new String[] { "*.txt", "*.json" },
				Text.translatable("roleplayersquill.import.filter").getString(),
				path -> {
					if (path == null) {
						return;
					}
					try {
						importFrom(path);
					} catch (IOException error) {
						RoleplayersQuill.LOGGER.warn("Could not read {}", path, error);
						say(Text.translatable("roleplayersquill.import.failed"));
					}
				});
	}

	private void importFrom(Path path) throws IOException {
		QuillConfig config = QuillConfig.get();
		if (BookIO.looksLikeDocument(path)) {
			QuillDocument read = BookIO.readDocument(path);
			if (read == null) {
				say(Text.translatable("roleplayersquill.import.failed"));
				return;
			}
			confirmThen(Text.translatable("roleplayersquill.import.replace"),
					Text.translatable("roleplayersquill.import.replace.detail", read.pageCount()),
					() -> {
						editor.document().mark();
						editor.document().pages().clear();
						editor.document().pages().addAll(read.pages());
						editor.document().setTitle(read.title());
						editor.setPage(0);
						editor.touch();
					});
			return;
		}

		String text = BookIO.readText(path);
		List<Paragraph> incoming = Paginator.parse(text, config.importJoinLines,
				QuillStyle.PLAIN, config.defaultAlignment);
		int pages = Paginator.countPages(incoming, config.paginatorOptions());
		confirmThen(Text.translatable("roleplayersquill.import.confirm"),
				Text.translatable("roleplayersquill.import.confirm.detail", pages),
				() -> insertParagraphs(incoming));
	}

	private void exportFile() {
		client.setScreen(new ExportScreen(this, editor));
	}

	private void confirmThen(Text title, Text detail, Runnable action) {
		client.setScreen(new ConfirmScreen(yes -> {
			client.setScreen(this);
			if (yes) {
				action.run();
			}
		}, title, detail));
	}

	// ---- leaving --------------------------------------------------------------------------------------

	/**
	 * Leaves without writing anything, which is what closing a book does in vanilla.
	 *
	 * <p>That is worth keeping rather than improving on. A book you can back out of is a book you
	 * can experiment in: make a mess of a page, press escape, open it again and the mess is gone.
	 * An editor that saves on the way out takes that away and gives nothing back, since saving is
	 * one button and one keystroke away at any moment.
	 */
	@Override
	public void close() {
		keepDraft(true);
		Dictation.release();
		client.setScreen(null);
	}

	@Override
	public void removed() {
		keepDraft(true);
		Dictation.release();
	}

	/**
	 * Stops before a page goes out broken, and says which one.
	 *
	 * <p>The warning under the page speaks only for the page being looked at, and a book is written
	 * back from wherever the writer happened to stop. A page that outgrew its room earlier in the
	 * book is either refused by the server outright, losing the whole edit, or taken and drawn short
	 * for every reader – so it is worth turning to it and saying so instead.
	 *
	 * @return true when the book is fit to send
	 */
	public boolean checkPagesFit() {
		int broken = editor.firstPageThatOverflows();
		if (broken < 0) {
			return true;
		}
		editor.setPage(broken);
		say(Text.translatable("roleplayersquill.editor.pagebroken", broken + 1), 8000L);
		return false;
	}

	/** Writes the book back, and keeps the document beside it. Nothing else ever calls this. */
	public void save() {
		editor.document().trimTrailingEmptyPages();
		if (!checkPagesFit()) {
			return;
		}
		List<String> pages = editor.encodePages();
		BookSender.saveDraft(stack, hand, pages);
		BookIO.saveDraft(editor.document(), pages, ownerTag(hand));
		// A copy of the book as it goes out, so that a week of rewriting can be walked back through
		// long after undo has forgotten it.
		BookIO.keepVersion(editor.document(), pages);
		BookIO.writeLastSave(pages);
		// The server's copy is these pages now, so this is what the draft has to be filed under from
		// here on – otherwise reopening the book would look for a draft that was never written.
		draftKey = pages;
		draftWritten = System.currentTimeMillis();
		editor.markSaved();
		say(Text.translatable("roleplayersquill.editor.saved"));
	}

	/**
	 * Keeps the draft up to date while the book is being written.
	 *
	 * <p>Nothing is sent anywhere: this is a copy on this computer, filed under the book as the
	 * server still holds it, so that a crash, a disconnection or a closed window cost nothing. The
	 * history goes with it, which is why undo still works the next time the book is opened.
	 *
	 * <p>Not on every keystroke, but within a second of one. A file per letter is a file per letter.
	 */
	private void keepDraft(boolean now) {
		if (!editor.isDirty()) {
			return;
		}
		long moment = System.currentTimeMillis();
		if (!now && moment - draftWritten < 1000L) {
			return;
		}
		draftWritten = moment;
		BookIO.saveDraft(editor.document(), draftKey, ownerTag(hand));
	}

	private void saveAndClose() {
		save();
		Dictation.release();
		client.setScreen(null);
	}

	public PageEditor editor() {
		return editor;
	}

	@Override
	public boolean shouldPause() {
		return true;
	}

	/** The same rule the game applies to what may be typed anywhere: no control characters. */
	private static final class StringHelperCompat {
		static boolean isValid(char chr) {
			// The section sign is refused on the way in: this editor writes its own, and one typed by
			// hand would be taken for formatting and then be impossible to see or delete.
			return chr != LegacyCodec.SECTION && chr >= 32 && chr != 127;
		}
	}
}
