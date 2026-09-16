package com.glamardor.roleplayersquill.screen;

import com.glamardor.roleplayersquill.speech.DownloadState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;

/**
 * "Something is happening, and here is how far along it is."
 *
 * <p>The first time dictation is switched on it fetches a recognition engine and then a model, and
 * the model is 162 MB. In silence that is indistinguishable from being broken, and the natural
 * response to a program that appears broken is to close it – which throws the download away and
 * starts it again from nothing the next time.
 *
 * <p>Lifted from Voice Subtitles, which learned this the same way.
 */
public final class ProgressOverlay {
	private static final int BAR_WIDTH = 160;
	private static final int BAR_HEIGHT = 3;
	private static final int GAP = 3;
	private static final int TEXT_HEIGHT = 9;

	private ProgressOverlay() {
	}

	/** @return the height it drew, so a caller can move whatever sits above it */
	public static int render(DrawContext context, TextRenderer textRenderer, int centreX, int bottom) {
		DownloadState.Snapshot download = DownloadState.snapshot();
		if (download == null) {
			return 0;
		}
		return draw(context, textRenderer, centreX, bottom, describe(download), download.fraction());
	}

	/**
	 * The line above the bar: what, how far, how fast, how much longer.
	 *
	 * <p>The time remaining only appears once the reading is worth trusting, so nobody is told
	 * "forty-five minutes left" by a connection that had not got going yet.
	 */
	private static Text describe(DownloadState.Snapshot download) {
		String done = megabytes(download.read());
		if (download.installing() || download.unpacking()) {
			// The bytes are in; what is left is checking and unpacking them, and for a large model
			// that is a wait of its own.
			return Text.translatable("roleplayersquill.download.installing", download.label(), done);
		}
		if (download.total() <= 0L) {
			return Text.translatable("roleplayersquill.download.unknown", download.label(), done);
		}
		String total = megabytes(download.total());
		long left = download.secondsLeft();
		if (left < 0L || download.elapsedMillis() < 3000L) {
			return Text.translatable("roleplayersquill.download.progress",
					download.label(), done, total, download.percent());
		}
		return Text.translatable("roleplayersquill.download.progress_eta",
				download.label(), done, total, download.percent(), time(left));
	}

	private static String megabytes(long bytes) {
		return String.format("%.1f", bytes / 1024F / 1024F);
	}

	private static Text time(long seconds) {
		return seconds < 60L
				? Text.translatable("roleplayersquill.time.seconds", seconds)
				: Text.translatable("roleplayersquill.time.minutes", (seconds + 30L) / 60L);
	}

	private static int draw(DrawContext context, TextRenderer textRenderer, int centreX, int bottom,
			Text label, float progress) {
		int textWidth = textRenderer.getWidth(label);
		int width = Math.max(textWidth, BAR_WIDTH);
		int left = centreX - width / 2;
		int top = bottom - TEXT_HEIGHT - GAP - BAR_HEIGHT;

		context.fill(left - 4, top - 3, left + width + 4, bottom + 1, ColorHelper.getArgb(180, 0, 0, 0));
		context.drawTextWithShadow(textRenderer, label, centreX - textWidth / 2, top, 0xFFE0C070);

		int barLeft = centreX - BAR_WIDTH / 2;
		int barTop = bottom - BAR_HEIGHT;
		context.fill(barLeft, barTop, barLeft + BAR_WIDTH, barTop + BAR_HEIGHT,
				ColorHelper.getArgb(160, 40, 40, 40));

		if (progress < 0F) {
			// Nothing to measure against, so a strip walks the bar rather than claiming a position
			// it does not know.
			int strip = BAR_WIDTH / 5;
			int x = barLeft + (int) ((System.currentTimeMillis() / 12L) % (BAR_WIDTH - strip));
			context.fill(x, barTop, x + strip, barTop + BAR_HEIGHT, ColorHelper.getArgb(220, 224, 192, 112));
		} else {
			int filled = Math.max(1, Math.round(BAR_WIDTH * Math.min(1F, progress)));
			context.fill(barLeft, barTop, barLeft + filled, barTop + BAR_HEIGHT,
					ColorHelper.getArgb(220, 224, 192, 112));
		}
		return TEXT_HEIGHT + GAP + BAR_HEIGHT + 6;
	}
}
