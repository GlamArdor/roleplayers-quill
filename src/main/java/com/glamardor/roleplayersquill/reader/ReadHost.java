package com.glamardor.roleplayersquill.reader;

import net.minecraft.client.gui.screen.ingame.BookScreen;

/**
 * What {@link com.glamardor.roleplayersquill.mixin.BookScreenMixin} lets the rest of this package
 * reach into a {@code BookScreen} for, without the package itself having to be a mixin.
 *
 * <p>{@code contents} and {@code pageIndex} are private on the vanilla class; the mixin shadows
 * them and hands out read access here. Turning a page goes through {@link #jumpTo}, not a field
 * write – on a lectern that is overridden to tell the server as well, which is how everyone else
 * looking at the same lectern sees the page turn too.
 */
public interface ReadHost {
	BookScreen.Contents contents();

	int pageIndex();

	void jumpTo(int index);

	BookScreen asScreen();

	/** Rebuilds the screen's widgets – {@code clearAndInit()}, which is protected on the target. */
	void reinit();
}
