package com.glamardor.roleplayersquill.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shift on the arrows of the game's own book editor – the one a book opened while sneaking gets –
 * goes to the first page or the last one, as it does in this mod's editor.
 *
 * <p>The forward arrow never adds a page while shift is held: on the last page it does nothing,
 * because holding shift asks for the end of the book, not for a longer one.
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen {
	@Shadow
	private int currentPage;

	// All three are private in the target, so they are shadowed as private stubs, not as abstract.
	@Shadow
	private int countPages() {
		throw new AssertionError();
	}

	@Shadow
	private void updatePage() {
		throw new AssertionError();
	}

	@Shadow
	private void updatePreviousPageButtonVisibility() {
		throw new AssertionError();
	}

	protected BookEditScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "openPreviousPage", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$toFirstPage(CallbackInfo ci) {
		if (Screen.hasShiftDown()) {
			roleplayersquill$goTo(0);
			ci.cancel();
		}
	}

	@Inject(method = "openNextPage", at = @At("HEAD"), cancellable = true)
	private void roleplayersquill$toLastPage(CallbackInfo ci) {
		if (Screen.hasShiftDown()) {
			roleplayersquill$goTo(this.countPages() - 1);
			ci.cancel();
		}
	}

	@Unique
	private void roleplayersquill$goTo(int page) {
		if (page != this.currentPage && page >= 0) {
			this.currentPage = page;
			this.updatePage();
		}
		this.updatePreviousPageButtonVisibility();
	}
}
