package com.glamardor.roleplayersquill.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.glamardor.roleplayersquill.screen.Input;

import com.glamardor.roleplayersquill.reader.ReadHost;
import com.glamardor.roleplayersquill.reader.ReadTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Everything this mod adds to reading a signed book: the side buttons, the header, the find strip
 * and selecting text – over a lectern exactly as over a book in a hand, since {@code
 * LecternScreen extends BookScreen} and overrides none of the four methods reached into here.
 *
 * <p>Only ever additive. Every hook either runs at the tail of the vanilla method or reads what it
 * did afterwards; nothing here changes what {@code BookScreen} itself draws or decides, on the
 * model of {@link ScreenMixin} – a future version of the game that renamed one of these methods
 * would fail this mixin's {@code require} and refuse to start rather than draw a book wrong.
 */
@Mixin(BookViewScreen.class)
public abstract class BookScreenMixin extends Screen implements ReadHost {
	@Shadow
	private BookViewScreen.BookAccess bookAccess;
	@Shadow
	private int currentPage;

	@Shadow
	protected abstract boolean forcePage(int index);

	@Unique
	private ReadTools roleplayersquill$tools;

	/**
	 * Set once, the first time {@code init} runs, and never again – the same rule the editor uses:
	 * sneaking when a book is opened leaves it to the vanilla screen and whatever else adds to it,
	 * rather than flickering the buttons on and off as a key is held partway through reading.
	 */
	@Unique
	private boolean roleplayersquill$suppressed;

	protected BookScreenMixin(Component title) {
		super(title);
	}

	// ---- ReadHost, so the reader package never has to be a mixin itself -------------------------

	@Override
	public BookViewScreen.BookAccess contents() {
		return this.bookAccess;
	}

	@Override
	public int pageIndex() {
		return this.currentPage;
	}

	@Override
	public void jumpTo(int index) {
		this.forcePage(index);
	}

	@Override
	public BookViewScreen asScreen() {
		return (BookViewScreen) (Object) this;
	}

	@Override
	public void tickTools() {
		if (roleplayersquill$tools != null && !roleplayersquill$suppressed) {
			roleplayersquill$tools.keepFindFocus();
		}
	}

	@Override
	public void reinit() {
		this.rebuildWidgets();
	}

	// ---- hooks --------------------------------------------------------------------------------------

	@Inject(method = "init", at = @At("TAIL"))
	private void roleplayersquill$init(CallbackInfo ci) {
		if (roleplayersquill$tools == null) {
			roleplayersquill$suppressed = Minecraft.getInstance().hasShiftDown();
			roleplayersquill$tools = new ReadTools(this);
			// Once per book opened, not once per resize: init runs again every time the window
			// changes shape or the find strip is toggled, and a copy kept per keystroke of that
			// would be the same file written twenty times for nothing.
			roleplayersquill$tools.keepOnOpen();
			if (!roleplayersquill$suppressed) {
				roleplayersquill$tools.openAtBookmark();
			}
		}
		if (!roleplayersquill$suppressed) {
			roleplayersquill$tools.addWidgets(this.width, this.height, this::addRenderableWidget, this.font);
		}
	}

	/**
	 * The one thing this mod draws under the screen's own widgets rather than over them.
	 *
	 * <p>The book itself is drawn in {@code renderBackground}, which has already run by the time
	 * this does, and the widgets are drawn by {@code render} itself – so this is the gap between
	 * the two, and the only place a backing for a widget can go.
	 */
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void roleplayersquill$renderUnder(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta,
			CallbackInfo ci) {
		if (roleplayersquill$tools != null && !roleplayersquill$suppressed) {
			roleplayersquill$tools.renderUnder(context);
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void roleplayersquill$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (roleplayersquill$tools != null && !roleplayersquill$suppressed) {
			roleplayersquill$tools.extractRenderState(context, this.font, this.width, this.height, mouseX, mouseY);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$mouseClicked(MouseButtonEvent event, boolean doubleClick,
			CallbackInfoReturnable<Boolean> info) {
		double mouseX = event.x(), mouseY = event.y();
		int button = Input.button(event);
		if (roleplayersquill$tools != null && !roleplayersquill$suppressed
				&& roleplayersquill$tools.ribbonClicked(button, this.width, mouseX, mouseY)) {
			info.setReturnValue(true);
			return;
		}
		if (roleplayersquill$tools != null && !roleplayersquill$suppressed) {
			roleplayersquill$tools.mousePressed(button, this.font, this.width, mouseX, mouseY);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> info) {
		int keyCode = event.key();
		if (roleplayersquill$tools != null && !roleplayersquill$suppressed
				&& roleplayersquill$tools.keyPressed(keyCode, this.font)) {
			info.setReturnValue(true);
		}
	}

	/**
	 * Shift on the arrows goes to the first page or the last one.
	 *
	 * <p>Here rather than on the buttons, because the arrow keys' neighbours Page Up and Page Down
	 * press the same buttons and should do the same thing. Not tied to {@code suppressed}: it adds
	 * nothing to the screen, and a book opened sneaking is the one most likely to be leafed through.
	 * The lectern overrides both methods and has a mixin of its own.
	 */
	@Inject(method = "pageBack", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$toFirstPage(CallbackInfo ci) {
		if (Minecraft.getInstance().hasShiftDown()) {
			this.forcePage(0);
			ci.cancel();
		}
	}

	@Inject(method = "pageForward", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$toLastPage(CallbackInfo ci) {
		if (Minecraft.getInstance().hasShiftDown()) {
			this.forcePage(this.bookAccess.getPageCount() - 1);
			ci.cancel();
		}
	}
}
