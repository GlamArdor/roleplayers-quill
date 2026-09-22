package com.glamardor.roleplayersquill.mixin;

import com.glamardor.roleplayersquill.reader.ReadHost;
import com.glamardor.roleplayersquill.reader.ReadTools;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.Text;
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
@Mixin(BookScreen.class)
public abstract class BookScreenMixin extends Screen implements ReadHost {
	@Shadow
	private BookScreen.Contents contents;
	@Shadow
	private int pageIndex;

	@Shadow
	protected abstract boolean jumpToPage(int index);

	@Unique
	private ReadTools roleplayersquill$tools;

	/**
	 * Set once, the first time {@code init} runs, and never again – the same rule the editor uses:
	 * sneaking when a book is opened leaves it to the vanilla screen and whatever else adds to it,
	 * rather than flickering the buttons on and off as a key is held partway through reading.
	 */
	@Unique
	private boolean roleplayersquill$suppressed;

	protected BookScreenMixin(Text title) {
		super(title);
	}

	// ---- ReadHost, so the reader package never has to be a mixin itself -------------------------

	@Override
	public BookScreen.Contents contents() {
		return this.contents;
	}

	@Override
	public int pageIndex() {
		return this.pageIndex;
	}

	@Override
	public void jumpTo(int index) {
		this.jumpToPage(index);
	}

	@Override
	public BookScreen asScreen() {
		return (BookScreen) (Object) this;
	}

	@Override
	public void reinit() {
		this.clearAndInit();
	}

	// ---- hooks --------------------------------------------------------------------------------------

	@Inject(method = "init", at = @At("TAIL"))
	private void roleplayersquill$init(CallbackInfo ci) {
		if (roleplayersquill$tools == null) {
			roleplayersquill$suppressed = Screen.hasShiftDown();
			roleplayersquill$tools = new ReadTools(this);
		}
		if (!roleplayersquill$suppressed) {
			roleplayersquill$tools.addWidgets(this.width, this.height, this::addDrawableChild, this.textRenderer);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void roleplayersquill$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (roleplayersquill$tools != null && !roleplayersquill$suppressed) {
			roleplayersquill$tools.render(context, this.textRenderer, this.width, this.height, mouseX, mouseY);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"))
	private void roleplayersquill$mouseClicked(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> info) {
		if (roleplayersquill$tools != null && !roleplayersquill$suppressed) {
			roleplayersquill$tools.mousePressed(button, this.textRenderer, this.width, mouseX, mouseY);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$keyPressed(int keyCode, int scanCode, int modifiers,
			CallbackInfoReturnable<Boolean> info) {
		if (roleplayersquill$tools != null && !roleplayersquill$suppressed
				&& roleplayersquill$tools.keyPressed(keyCode, this.textRenderer)) {
			info.setReturnValue(true);
		}
	}
}
