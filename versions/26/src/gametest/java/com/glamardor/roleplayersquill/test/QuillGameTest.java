package com.glamardor.roleplayersquill.test;

import com.glamardor.roleplayersquill.RoleplayersQuillClient;
import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.gui.ConfigScreenFactory;
import com.glamardor.roleplayersquill.screen.IconButton;
import com.glamardor.roleplayersquill.screen.PageEditor;
import com.glamardor.roleplayersquill.screen.QuillEditScreen;
import com.glamardor.roleplayersquill.screen.Symbols;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Drives the mod in a real client: a world, a book in hand, real key presses and clicks, and the
 * server asked afterwards what it received. Results go to quill-test.log in the run folder, and
 * the screenshots to screenshots/.
 */
public final class QuillGameTest implements FabricClientGameTest {
	private static final int Y = 100;
	private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
	private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

	private final List<String> log = new ArrayList<>();
	private int failures;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0));
		try {
			startup(ctx);
			try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
				TestServerContext server = sp.getServer();
				setup(ctx, server);
				step("editor", () -> editor(ctx, server));
				step("editor buttons", () -> editorButtons(ctx, server));
				step("sneak opens the vanilla editor", () -> vanillaEditor(ctx, server));
				step("reading a signed book", () -> reading(ctx, server));
				step("lectern", () -> lectern(ctx, server));
				step("chat", () -> chat(ctx, server));
				step("sign", () -> sign(ctx, server));
				step("anvil", () -> anvil(ctx, server));
				step("creative: rich book", () -> creative(ctx, server));
				step("settings", () -> settings(ctx));
			}
		} catch (Throwable error) {
			fail("crashed: " + error);
			StringBuilder trace = new StringBuilder();
			for (StackTraceElement e : error.getStackTrace()) {
				trace.append("\n        at ").append(e);
			}
			info(trace.toString());
		}
		finish();
	}

	// ---- steps ---------------------------------------------------------------------------------------

	private void startup(ClientGameTestContext ctx) {
		log.add("== startup");
		boolean loaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("roleplayersquill");
		check("mod is loaded", loaded);
		check("key bindings registered", RoleplayersQuillClient.dictateKey != null && RoleplayersQuillClient.settingsKey != null);
		boolean bound = ctx.computeOnClient(mc -> {
			for (var key : mc.options.keyMappings) {
				if (key == RoleplayersQuillClient.dictateKey) {
					return true;
				}
			}
			return false;
		});
		check("dictate key is in the controls list", bound);
		// The font doors: Symbols asks the font set for glyphs through three accessors.
		float[] star = ctx.computeOnClient(mc -> Symbols.drawnSize('★'));
		check("font accessors: a glyph the font has is measured", star != null && star[0] > 0 && star[1] > 0);
		if (star != null) {
			info("★ drawn " + star[0] + " x " + star[1]);
		}
		float[] letter = ctx.computeOnClient(mc -> Symbols.drawnSize('A'));
		info("A drawn " + (letter == null ? "null" : letter[0] + " x " + letter[1]));
		check("font accessors: 'A' measured as a letter", letter != null && letter[0] >= 4 && letter[0] <= 8 && letter[1] >= 7);
		ctx.takeScreenshot("quill_00_title");
	}

	private void setup(ClientGameTestContext ctx, TestServerContext server) {
		ctx.waitTicks(20);
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("fill -8 " + (Y - 1) + " -8 8 " + (Y - 1) + " 8 minecraft:stone");
		server.runCommand("fill -8 " + Y + " -8 8 " + (Y + 4) + " 8 minecraft:air");
		server.runCommand("tp @a 0.5 " + Y + " 0.5 0 0");
		ctx.waitTicks(20);
	}

	private void editor(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:writable_book");
		ctx.waitTicks(5);
		TestInput input = ctx.getInput();
		input.lookAt(0, -90); // at the floor, so the use key uses the item
		input.pressKey(options -> options.keyUse);
		ctx.waitForScreen(QuillEditScreen.class);
		check("using a writable book opens the Quill editor", screenIs(ctx, QuillEditScreen.class));
		// The test types through charTyped directly, past SDL; a real keyboard only gets letters
		// through while SDL text input is on.
		check("SDL text input is on in the editor", textInputOn(ctx));
		ctx.waitTicks(5);
		ctx.takeScreenshot("quill_01_editor_empty");

		input.typeChars("Hello, мир! ");
		ctx.waitTicks(2);
		check("typing Latin and Cyrillic", pageText(ctx).startsWith("Hello, мир! "));

		// Ctrl+B, type, Ctrl+B
		input.holdControl();
		input.pressKey(InputConstants.KEY_B);
		input.releaseControl();
		input.typeChars("bold");
		input.holdControl();
		input.pressKey(InputConstants.KEY_B);
		input.releaseControl();
		input.typeChars(" plain");
		ctx.waitTicks(2);
		boolean boldRun = ctx.computeOnClient(mc -> {
			PageEditor editor = editor(mc);
			var paragraph = editor.currentPage().get(0);
			String text = paragraph.text();
			int at = text.indexOf("bold");
			return at >= 0 && paragraph.styleAt(at + 1).bold() && !paragraph.styleAt(text.indexOf("plain") + 1).bold();
		});
		check("Ctrl+B makes the typed run bold and switches off again", boldRun);

		// A letter outside the BMP arrives from SDL as one code point.
		input.typeChars(" 𝔈");
		ctx.waitTicks(2);
		check("a letter outside the BMP is typed whole", pageText(ctx).contains("𝔈"));

		// Enter, then a centred heading with Ctrl+E
		input.pressKey(InputConstants.KEY_RETURN);
		input.holdControl();
		input.pressKey(InputConstants.KEY_E);
		input.releaseControl();
		input.typeChars("Heading");
		ctx.waitTicks(2);
		boolean centred = ctx.computeOnClient(mc -> {
			var paragraphs = editor(mc).currentPage();
			return paragraphs.size() >= 2 && paragraphs.get(1).text().equals("Heading")
					&& paragraphs.get(1).alignment().name().equals("CENTER");
		});
		check("Enter makes a paragraph, Ctrl+E centres it", centred);

		// Backspace and arrows
		input.pressKey(InputConstants.KEY_BACKSPACE);
		check("Backspace deletes", pageText(ctx).endsWith("Headin"));
		input.typeChars("g");
		input.pressKey(InputConstants.KEY_LEFT);
		input.pressKey(InputConstants.KEY_LEFT);
		input.typeChars("X");
		check("arrow keys move the caret", pageText(ctx).endsWith("HeadiXng"));
		input.holdControl();
		input.pressKey(InputConstants.KEY_Z);
		input.releaseControl();
		ctx.waitTicks(2);
		info("after Ctrl+Z: " + pageText(ctx));
		check("Ctrl+Z undoes", !pageText(ctx).contains("X"));

		// Clicking in the text moves the caret: click at the start of the first line.
		double[] start = onBook(ctx, 36 + 1, 30 + 4);
		double[] further = onBook(ctx, 36 + 20, 30 + 4);
		int caretBefore = ctx.computeOnClient(mc -> editor(mc).paragraphIndex());
		click(ctx, start[0], start[1], LEFT);
		ctx.waitTicks(2);
		int[] caret = ctx.computeOnClient(mc -> new int[] {editor(mc).paragraphIndex(), editor(mc).caret()});
		info("caret after the click " + caret[0] + ":" + caret[1]);
		check("a click on the first line puts the caret there (was paragraph " + caretBefore + ")",
				caret[0] == 0 && caret[1] <= 1);

		// Drag-select across the first word with the mouse.
		ctx.getInput().setCursorPos(scaled(ctx, start[0]), scaled(ctx, start[1]));
		input.holdMouse(LEFT);
		ctx.waitTicks(1);
		ctx.getInput().setCursorPos(scaled(ctx, further[0]), scaled(ctx, further[1]));
		ctx.waitTicks(1);
		input.releaseMouse(LEFT);
		ctx.waitTicks(2);
		check("dragging selects", ctx.computeOnClient(mc -> editor(mc).hasSelection()));

		// Ctrl+A then Ctrl+I: everything italic
		input.holdControl();
		input.pressKey(InputConstants.KEY_A);
		input.pressKey(InputConstants.KEY_I);
		input.releaseControl();
		ctx.waitTicks(2);
		boolean italic = ctx.computeOnClient(mc -> editor(mc).currentPage().get(0).styleAt(1).italic());
		check("Ctrl+A, Ctrl+I", italic);
		input.holdControl();
		input.pressKey(InputConstants.KEY_Z);
		input.releaseControl();
		input.pressKey(InputConstants.KEY_END);

		// The bookmark: the spot left of the page number.
		double[] ribbon = onBook(ctx, 41, 12);
		click(ctx, ribbon[0], ribbon[1], LEFT);
		ctx.waitTicks(2);
		check("clicking the ribbon spot sets the bookmark", ctx.computeOnClient(mc -> editor(mc).bookmark()) == 0);
		ctx.takeScreenshot("quill_02_editor_typed");

		// Ctrl+Enter: a new page; Page Up/Down; Shift+Page Up to the first page.
		input.holdControl();
		input.pressKey(InputConstants.KEY_RETURN);
		input.releaseControl();
		input.typeChars("Second page");
		ctx.waitTicks(2);
		check("Ctrl+Enter starts a new page", ctx.computeOnClient(mc -> editor(mc).page()) == 1);
		input.pressKey(InputConstants.KEY_PAGEUP);
		check("Page Up turns back", ctx.computeOnClient(mc -> editor(mc).page()) == 0);
		input.pressKey(InputConstants.KEY_PAGEDOWN);
		check("Page Down turns on", ctx.computeOnClient(mc -> editor(mc).page()) == 1);
		input.holdShift();
		input.pressKey(InputConstants.KEY_PAGEUP);
		input.releaseShift();
		check("Shift+Page Up goes to the first page", ctx.computeOnClient(mc -> editor(mc).page()) == 0);

		// Ctrl+S writes the book back to the server.
		input.holdControl();
		input.pressKey(InputConstants.KEY_S);
		input.releaseControl();
		ctx.waitTicks(10);
		List<String> pages = serverPages(server);
		info("server pages: " + pages);
		check("Ctrl+S: the server has two pages", pages.size() == 2);
		check("the server page holds the text and a bold code",
				!pages.isEmpty() && pages.get(0).contains("Hello, мир!") && pages.get(0).contains("§l")
						&& pages.get(0).contains("Headin"));
		check("no black ink (§0) anywhere", pages.stream().noneMatch(p -> p.contains("§0")));

		// Esc closes the editor
		input.pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitTicks(5);
		check("Esc closes the editor", screenIs(ctx, null));
	}

	private void editorButtons(ClientGameTestContext ctx, TestServerContext server) {
		TestInput input = ctx.getInput();
		input.pressKey(options -> options.keyUse);
		ctx.waitForScreen(QuillEditScreen.class);
		ctx.waitTicks(5);
		check("the book opens again at the bookmark page", ctx.computeOnClient(mc -> editor(mc).page()) == 0);
		check("the saved text comes back", pageText(ctx).startsWith("Hello, мир!"));

		// Select the first word, then click the bold button with the mouse.
		input.pressKey(InputConstants.KEY_HOME);
		input.holdShift();
		for (int i = 0; i < 5; i++) {
			input.pressKey(InputConstants.KEY_RIGHT);
		}
		input.releaseShift();
		check("Shift+arrows select", ctx.computeOnClient(mc -> editor(mc).hasSelection()));
		clickButton(ctx, "roleplayersquill.tool.bold");
		ctx.waitTicks(2);
		check("the bold button (clicked) bolds the selection",
				ctx.computeOnClient(mc -> editor(mc).currentPage().get(0).styleAt(1).bold()));
		clickButton(ctx, "roleplayersquill.tool.align_right");
		check("the right-align button", ctx.computeOnClient(mc -> editor(mc).currentPage().get(0).alignment().name())
				.equals("RIGHT"));
		clickButton(ctx, "roleplayersquill.tool.align_left");

		// Every other button: it must do something and not take the game down.
		List<String> keys = ctx.computeOnClient(mc -> {
			List<String> found = new ArrayList<>();
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof IconButton button && button.visible && button.isActive()) {
					String key = keyOf(button.getMessage());
					if (key != null) {
						found.add(key);
					}
				}
			}
			return found;
		});
		info("toolbar buttons: " + keys.size() + " " + keys);
		check("the toolbar is there", keys.size() >= 30);
		int shot = 0;
		for (String key : keys) {
			// Skipped: these open the system file picker or start a download of a model or a
			// dictionary, which is for a person at the keyboard to check.
			if (key.endsWith(".import") || key.endsWith(".export") || key.endsWith(".dictate")
					|| key.endsWith(".spell") || key.endsWith(".save") || key.endsWith(".page_remove")
					|| key.endsWith(".page_clear") || key.endsWith(".bold") || key.endsWith(".align_right")) {
				continue;
			}
			Class<?> before = ctx.computeOnClient(mc -> mc.gui.screen() == null ? null : mc.gui.screen().getClass());
			boolean clicked = clickButton(ctx, key);
			ctx.waitTicks(3);
			Screen now = ctx.computeOnClient(mc -> mc.gui.screen());
			String where = now == null ? "no screen" : now.getClass().getSimpleName();
			check("button " + key + " (now on " + where + ")", clicked && now != null);
			ctx.takeScreenshot(String.format("quill_10_%02d_%s", shot++, key.substring(key.lastIndexOf('.') + 1)));
			if (now != null && now.getClass() != before) {
				input.pressKey(InputConstants.KEY_ESCAPE);
				ctx.waitTicks(3);
			}
			// Popups (colour, styles, ornaments) close on Esc too
			if (ctx.computeOnClient(mc -> mc.gui.screen() instanceof QuillEditScreen)) {
				input.pressKey(InputConstants.KEY_ESCAPE);
				ctx.waitTicks(2);
				if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof QuillEditScreen)) {
					// That Esc closed the editor itself: open it again.
					input.pressKey(options -> options.keyUse);
					ctx.waitForScreen(QuillEditScreen.class);
					ctx.waitTicks(3);
				}
			} else {
				ctx.setScreen(() -> null);
				ctx.waitTicks(2);
				input.pressKey(options -> options.keyUse);
				ctx.waitForScreen(QuillEditScreen.class);
				ctx.waitTicks(3);
			}
		}

		// Keyboard shortcuts that open windows
		openWith(ctx, InputConstants.KEY_G, "Ctrl+G symbols");
		openWith(ctx, InputConstants.KEY_T, "Ctrl+T table");
		openWith(ctx, InputConstants.KEY_K, "Ctrl+K link");
		openWith(ctx, InputConstants.KEY_F, "Ctrl+F find");
		ctx.waitTicks(2);
		check("text input is on again after the find box gave up the keyboard",
				screenIs(ctx, QuillEditScreen.class) && textInputOn(ctx));
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
	}

	private void openWith(ClientGameTestContext ctx, int key, String what) {
		TestInput input = ctx.getInput();
		if (!screenIs(ctx, QuillEditScreen.class)) {
			ctx.setScreen(() -> null);
			input.pressKey(options -> options.keyUse);
			ctx.waitForScreen(QuillEditScreen.class);
			ctx.waitTicks(3);
		}
		String before = ctx.computeOnClient(mc -> describe(mc.gui.screen()) + "/" + mc.gui.screen().children().size());
		input.holdControl();
		input.pressKey(key);
		input.releaseControl();
		ctx.waitTicks(3);
		String after = ctx.computeOnClient(mc -> describe(mc.gui.screen()) + "/" + mc.gui.screen().children().size());
		check(what + " (" + after + ")", !after.equals(before));
		ctx.takeScreenshot("quill_11_" + what.replace(' ', '_').replace('+', '_'));
		input.pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitTicks(3);
	}

	private void vanillaEditor(ClientGameTestContext ctx, TestServerContext server) {
		TestInput input = ctx.getInput();
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
		input.holdShift();
		input.pressKey(options -> options.keyUse);
		input.releaseShift();
		ctx.waitTicks(5);
		check("Shift + use opens the vanilla editor", screenIs(ctx, BookEditScreen.class));
		ctx.takeScreenshot("quill_20_vanilla_editor");
		if (screenIs(ctx, BookEditScreen.class)) {
			// Shift on the forward arrow goes to the last page: the book has two.
			input.holdShift();
			input.pressKey(InputConstants.KEY_PAGEDOWN);
			input.releaseShift();
			ctx.waitTicks(2);
			int page = ctx.computeOnClient(mc -> readInt(mc.gui.screen(), BookEditScreen.class, "currentPage"));
			check("Shift+Page Down in the vanilla editor goes to the last page (" + page + ")", page == 1);
			input.holdShift();
			input.pressKey(InputConstants.KEY_PAGEUP);
			input.releaseShift();
			ctx.waitTicks(2);
			page = ctx.computeOnClient(mc -> readInt(mc.gui.screen(), BookEditScreen.class, "currentPage"));
			check("Shift+Page Up goes to the first", page == 0);
		}
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
	}

	private void reading(ClientGameTestContext ctx, TestServerContext server) {
		TestInput input = ctx.getInput();
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:written_book[written_book_content={"
				+ "title:\"Test book\",author:\"Bob\",pages:[\"First page, see https://example.com\",\"Second\",\"Third\",\"Fourth\"]}]");
		ctx.waitTicks(5);
		input.pressKey(options -> options.keyUse);
		ctx.waitForScreen(BookViewScreen.class);
		ctx.waitTicks(5);
		List<String> keys = ctx.computeOnClient(mc -> {
			List<String> found = new ArrayList<>();
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof AbstractWidget widget) {
					String key = keyOf(widget.getMessage());
					if (key != null && key.startsWith("roleplayersquill")) {
						found.add(key);
					}
				}
			}
			return found;
		});
		info("reader buttons: " + keys);
		check("the reading strip is beside the book", keys.size() >= 5);
		ctx.takeScreenshot("quill_30_reading");
		boolean linked = ctx.computeOnClient(mc -> {
			Component page = BookViewScreen.BookAccess.fromItem(mc.player.getMainHandItem()).getPage(0);
			boolean[] found = {false};
			page.visit((style, text) -> {
				if (style.getClickEvent() != null) {
					found[0] = true;
				}
				return java.util.Optional.empty();
			}, net.minecraft.network.chat.Style.EMPTY);
			return found[0];
		});
		check("an address in a signed book becomes a link", linked);

		input.holdShift();
		input.pressKey(InputConstants.KEY_PAGEDOWN);
		input.releaseShift();
		ctx.waitTicks(2);
		int page = ctx.computeOnClient(mc -> readInt(mc.gui.screen(), BookViewScreen.class, "currentPage"));
		check("Shift+Page Down: the last page (" + page + ")", page == 3);
		input.holdShift();
		input.pressKey(InputConstants.KEY_PAGEUP);
		input.releaseShift();
		ctx.waitTicks(2);
		page = ctx.computeOnClient(mc -> readInt(mc.gui.screen(), BookViewScreen.class, "currentPage"));
		check("Shift+Page Up: the first page", page == 0);

		// Ctrl+F opens the find strip in the reader
		int before = ctx.computeOnClient(mc -> mc.gui.screen().children().size());
		input.holdControl();
		input.pressKey(InputConstants.KEY_F);
		input.releaseControl();
		ctx.waitTicks(3);
		int after = ctx.computeOnClient(mc -> mc.gui.screen().children().size());
		check("Ctrl+F in the reader opens the find strip (" + before + " -> " + after + ")", after > before);
		for (int i = 0; i < 4; i++) {
			info("tick " + i + ": " + ctx.<String, RuntimeException>computeOnClient(mc -> {
				StringBuilder all = new StringBuilder(describe(mc.gui.screen())).append(" focus=");
				var focused = mc.gui.screen().getFocused();
				all.append(focused == null ? "-" : focused.getClass().getSimpleName()).append(" :");
				for (GuiEventListener child : mc.gui.screen().children()) {
					all.append(' ').append(child.getClass().getSimpleName());
				}
				return all.toString();
			}));
			ctx.waitTick();
		}
		info("focused before typing: " + ctx.<String, RuntimeException>computeOnClient(mc -> {
			var focused = mc.gui.screen().getFocused();
			return focused == null ? "nothing" : focused.getClass().getName();
		}));
		input.typeChars("Third");
		ctx.waitTicks(3);
		info("find boxes after typing: " + ctx.<String, RuntimeException>computeOnClient(mc -> {
			StringBuilder boxes = new StringBuilder();
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof EditBox box) {
					boxes.append('[').append(box.getValue()).append(box.isFocused() ? " focused" : "").append(']');
				}
			}
			return boxes.toString();
		}));
		ctx.takeScreenshot("quill_31_reading_find");
		input.pressKey(InputConstants.KEY_RETURN);
		ctx.waitTicks(2);
		page = ctx.computeOnClient(mc -> readInt(mc.gui.screen(), BookViewScreen.class, "currentPage"));
		check("finding a word turns to its page (" + page + ")", page == 2);

		for (String key : keys) {
			if (key.endsWith(".export")) {
				continue;
			}
			if (!screenIs(ctx, BookViewScreen.class)) {
				ctx.setScreen(() -> null);
				input.pressKey(options -> options.keyUse);
				ctx.waitForScreen(BookViewScreen.class);
				ctx.waitTicks(3);
			}
			boolean clicked = clickButton(ctx, key);
			ctx.waitTicks(3);
			String now = ctx.computeOnClient(mc -> describe(mc.gui.screen()));
			check("reader button " + key + " (now " + now + ")", clicked);
			ctx.takeScreenshot("quill_32_" + key.substring(key.lastIndexOf('.') + 1));
		}
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
	}

	private void lectern(ClientGameTestContext ctx, TestServerContext server) {
		TestInput input = ctx.getInput();
		server.runCommand("setblock 0 " + Y + " 2 minecraft:lectern[facing=north,has_book=true]{Book:{id:\"minecraft:written_book\",count:1,"
				+ "components:{\"minecraft:written_book_content\":{title:\"Lectern\",author:\"Bob\",pages:[\"One\",\"Two\",\"Three\"]}}}}");
		ctx.waitTicks(5);
		input.lookAt(new BlockPos(0, Y, 2));
		ctx.waitTicks(2);
		input.pressKey(options -> options.keyUse);
		ctx.waitTicks(10);
		check("the lectern opens", screenIs(ctx, LecternScreen.class));
		ctx.takeScreenshot("quill_40_lectern");
		if (screenIs(ctx, LecternScreen.class)) {
			input.holdShift();
			input.pressKey(InputConstants.KEY_PAGEDOWN);
			input.releaseShift();
			ctx.waitTicks(10);
			int page = server.computeOnServer(s -> ((LecternBlockEntity) s.overworld().getBlockEntity(new BlockPos(0, Y, 2))).getPage());
			check("Shift+Page Down on a lectern turns the server's page to the last (" + page + ")", page == 2);
		}
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
		server.runCommand("setblock 0 " + Y + " 2 minecraft:air");
	}

	private void chat(ClientGameTestContext ctx, TestServerContext server) {
		TestInput input = ctx.getInput();
		boolean symbols = QuillConfig.get().chatSymbols;
		QuillConfig.get().chatSymbols = false;
		ctx.runOnClient(mc -> mc.gui.openChatScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE));
		ctx.waitTicks(3);
		info("vanilla-opened chat with the bar off, after 3 ticks: " + ctx.<String, RuntimeException>computeOnClient(mc -> describe(mc.gui.screen())));
		ctx.setScreen(() -> null);
		QuillConfig.get().chatSymbols = symbols;
		ctx.runOnClient(mc -> mc.gui.openChatScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE));
		ctx.waitTicks(3);
		info("vanilla-opened chat with the bar on, after 3 ticks: " + ctx.<String, RuntimeException>computeOnClient(mc -> describe(mc.gui.screen())));
		ctx.setScreen(() -> new ChatScreen("", false));
		ctx.waitForScreen(ChatScreen.class);
		StringBuilder seen = new StringBuilder();
		for (int i = 0; i < 5; i++) {
			ctx.waitTick();
			seen.append(ctx.<String, RuntimeException>computeOnClient(mc -> describe(mc.gui.screen()))).append(' ');
		}
		info("chat over five ticks: " + seen);
		List<String> keys = ctx.computeOnClient(mc -> {
			List<String> found = new ArrayList<>();
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof IconButton button) {
					found.add(button.getMessage().getString());
				}
			}
			return found;
		});
		info("chat bar: " + keys.size() + " buttons");
		check("the symbol row is over the chat box", keys.size() >= 5);
		input.typeChars("Test ");
		// The first symbol button: whatever it is, it goes into the box.
		String insert = ctx.computeOnClient(mc -> {
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof IconButton button && button.visible && single(button)) {
					return button.getMessage().getString();
				}
			}
			return null;
		});
		boolean clicked = clickWidget(ctx, w -> w instanceof IconButton && w.visible && single(w));
		ctx.waitTicks(2);
		String value = ctx.computeOnClient(mc -> chatBox(mc).getValue());
		info("chat box after a click on '" + insert + "': " + value);
		check("clicking a symbol puts it into the chat box", clicked && value.length() > "Test ".length());
		ctx.takeScreenshot("quill_50_chat");
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
	}

	private void sign(ClientGameTestContext ctx, TestServerContext server) {
		TestInput input = ctx.getInput();
		server.runCommand("setblock 0 " + Y + " 3 minecraft:stone");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:oak_sign");
		ctx.waitTicks(5);
		input.lookAt(new BlockPos(0, Y, 3));
		ctx.waitTicks(2);
		input.pressKey(options -> options.keyUse);
		ctx.waitForScreen(AbstractSignEditScreen.class);
		ctx.waitTicks(3);
		int ours = ctx.computeOnClient(mc -> {
			int count = 0;
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof IconButton) {
					count++;
				}
			}
			return count;
		});
		check("the sign editor has the toolbar (" + ours + " buttons)", ours >= 3);
		input.typeChars("Shop ");
		boolean clicked = clickWidget(ctx, w -> w instanceof IconButton && w.visible
				&& w.getMessage().getString().codePointCount(0, w.getMessage().getString().length()) == 1);
		ctx.waitTicks(2);
		ctx.takeScreenshot("quill_60_sign");
		ctx.clickScreenButton("gui.done");
		ctx.waitTicks(10);
		String text = server.computeOnServer(s -> {
			for (BlockPos pos : BlockPos.betweenClosed(-1, Y, 1, 1, Y + 1, 3)) {
				if (s.overworld().getBlockEntity(pos) instanceof SignBlockEntity sign) {
					return sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(0).getString();
				}
			}
			return null;
		});
		info("sign text on the server: " + text);
		check("the sign is written, with the symbol", clicked && text != null && text.startsWith("Shop ") && text.length() > 5);
		server.runCommand("fill -1 " + Y + " 1 1 " + (Y + 1) + " 3 minecraft:air");
	}

	private void anvil(ClientGameTestContext ctx, TestServerContext server) {
		TestInput input = ctx.getInput();
		server.runCommand("setblock 0 " + Y + " 2 minecraft:anvil");
		ctx.waitTicks(3);
		input.lookAt(new BlockPos(0, Y, 2));
		ctx.waitTicks(2);
		input.pressKey(options -> options.keyUse);
		ctx.waitForScreen(AnvilScreen.class);
		ctx.waitTicks(3);
		int ours = ctx.computeOnClient(mc -> {
			int count = 0;
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof IconButton) {
					count++;
				}
			}
			return count;
		});
		check("the anvil has the bar (" + ours + " buttons)", ours >= 1);
		ctx.takeScreenshot("quill_70_anvil");
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
		server.runCommand("setblock 0 " + Y + " 2 minecraft:air");
	}

	private void creative(ClientGameTestContext ctx, TestServerContext server) {
		TestInput input = ctx.getInput();
		server.runCommand("gamemode creative @a");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:writable_book");
		ctx.waitTicks(5);
		input.lookAt(0, -90);
		input.pressKey(options -> options.keyUse);
		ctx.waitForScreen(QuillEditScreen.class);
		ctx.waitTicks(3);
		input.typeChars("A link: ");
		// The link window, filled through its own text boxes.
		input.holdControl();
		input.pressKey(InputConstants.KEY_K);
		input.releaseControl();
		ctx.waitTicks(3);
		String linkScreen = ctx.computeOnClient(mc -> describe(mc.gui.screen()));
		info("Ctrl+K opened " + linkScreen);
		ctx.takeScreenshot("quill_80_link");
		input.pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitTicks(3);
		input.holdControl();
		input.pressKey(InputConstants.KEY_S);
		input.releaseControl();
		ctx.waitTicks(10);
		List<String> pages = serverPages(server);
		check("saving in creative still writes the book", !pages.isEmpty() && pages.get(0).startsWith("A link:"));
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
		server.runCommand("gamemode survival @a");
	}

	private void settings(ClientGameTestContext ctx) {
		ctx.setScreen(() -> ConfigScreenFactory.create(null));
		ctx.waitTicks(5);
		String cloth = ctx.computeOnClient(mc -> describe(mc.gui.screen()));
		check("the settings screen opens (" + cloth + ")", !cloth.equals("no screen"));
		ctx.takeScreenshot("quill_90_settings");
		ctx.setScreen(() -> new com.glamardor.roleplayersquill.gui.FallbackConfigScreen(null));
		ctx.waitTicks(5);
		check("the built-in settings screen opens", screenIs(ctx, com.glamardor.roleplayersquill.gui.FallbackConfigScreen.class));
		ctx.getInput().typeChars("hyph");
		ctx.waitTicks(3);
		ctx.takeScreenshot("quill_91_settings_builtin");
		ctx.getInput().scroll(-3);
		ctx.waitTicks(2);
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
		boolean viaModMenu = ctx.computeOnClient(mc -> {
			var api = new com.glamardor.roleplayersquill.gui.ModMenuIntegration();
			return api.getModConfigScreenFactory().create(null) != null;
		});
		check("Mod Menu integration makes a screen", viaModMenu);
	}

	// ---- helpers -------------------------------------------------------------------------------------

	private interface Step {
		void run();
	}

	private void step(String name, Step step) {
		// QUILL_STEPS=reading,chat runs only the steps whose names start with one of those.
		String only = System.getenv("QUILL_STEPS");
		if (only != null && !only.isBlank()
				&& java.util.Arrays.stream(only.split(",")).noneMatch(prefix -> name.startsWith(prefix.trim()))) {
			return;
		}
		log.add("== " + name);
		try {
			step.run();
		} catch (Throwable error) {
			fail(name + " crashed: " + error);
			StringBuilder trace = new StringBuilder();
			for (StackTraceElement e : error.getStackTrace()) {
				trace.append("\n        at ").append(e);
				if (e.getClassName().startsWith("net.fabricmc.fabric.impl.client.gametest")) {
					break;
				}
			}
			info(trace.toString());
		}
	}

	private static PageEditor editor(Minecraft mc) {
		return (PageEditor) read(mc.gui.screen(), QuillEditScreen.class, "editor");
	}

	private static boolean textInputOn(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> (Boolean) read(mc.textInputManager(),
				com.mojang.blaze3d.platform.TextInputManager.class, "textInputEnabled"));
	}

	private static String pageText(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			StringBuilder text = new StringBuilder();
			for (var paragraph : editor(mc).currentPage()) {
				text.append(paragraph.text());
			}
			return text.toString();
		});
	}

	/** Where a point of the book texture is on the screen, in GUI pixels. */
	private static double[] onBook(ClientGameTestContext ctx, double x, double y) {
		return ctx.computeOnClient(mc -> {
			float scale = (Float) read(mc.gui.screen(), QuillEditScreen.class, "scale");
			return new double[] {readInt(mc.gui.screen(), QuillEditScreen.class, "bookX") + x * scale,
					readInt(mc.gui.screen(), QuillEditScreen.class, "bookY") + y * scale};
		});
	}

	private static EditBox chatBox(Minecraft mc) {
		return (EditBox) read(mc.gui.screen(), ChatScreen.class, "input");
	}

	private static List<String> serverPages(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().get(0);
			ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
			WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
			return content == null ? List.<String>of() : content.getPages(false).toList();
		});
	}

	private static Object read(Object target, Class<?> owner, String name) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException(error);
		}
	}

	private static int readInt(Object target, Class<?> owner, String name) {
		return (Integer) read(target, owner, name);
	}

	private static String keyOf(Component message) {
		return message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
	}

	/** A button whose label is one character: a symbol, not an arrow or a code. */
	private static boolean single(AbstractWidget widget) {
		String label = widget.getMessage().getString();
		return label.codePointCount(0, label.length()) == 1;
	}

	private static String describe(Screen screen) {
		return screen == null ? "no screen" : screen.getClass().getSimpleName();
	}

	private static boolean screenIs(ClientGameTestContext ctx, Class<?> type) {
		return ctx.computeOnClient(mc -> type == null ? mc.gui.screen() == null : type.isInstance(mc.gui.screen()));
	}

	private static double scaled(ClientGameTestContext ctx, double guiCoordinate) {
		return ctx.computeOnClient(mc -> guiCoordinate * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth());
	}

	private static void click(ClientGameTestContext ctx, double x, double y, int button) {
		ctx.getInput().setCursorPos(scaled(ctx, x), scaled(ctx, y));
		ctx.waitTick();
		ctx.getInput().pressMouse(button);
		ctx.waitTick();
	}

	private static boolean clickButton(ClientGameTestContext ctx, String key) {
		return clickWidget(ctx, w -> key.equals(keyOf(w.getMessage())) && w.visible && w.active);
	}

	private static boolean clickWidget(ClientGameTestContext ctx, Predicate<AbstractWidget> which) {
		double[] at = ctx.computeOnClient(mc -> {
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof AbstractWidget widget && which.test(widget)) {
					return new double[] {widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0};
				}
			}
			return null;
		});
		if (at == null) {
			return false;
		}
		click(ctx, at[0], at[1], LEFT);
		return true;
	}

	private void info(String line) {
		log.add("     " + line);
	}

	private void check(String what, boolean ok) {
		log.add((ok ? "ok   " : "FAIL ") + what);
		if (!ok) {
			failures++;
		}
	}

	private void fail(String what) {
		log.add("FAIL " + what);
		failures++;
	}

	private void finish() {
		log.add(failures == 0 ? "PASSED" : failures + " FAILED");
		try {
			Files.write(Path.of("quill-test.log"), log);
		} catch (IOException ignored) {
		}
	}
}
