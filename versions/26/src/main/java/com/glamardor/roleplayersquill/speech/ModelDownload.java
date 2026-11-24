package com.glamardor.roleplayersquill.speech;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Fetching a recognition model, in the background, at most one at a time.
 *
 * <p>Shared by the automatic first-run download and the {@code /vsub model
 * download} command, so the two can never race each other into the same folder.
 *
 * <p>Progress is reported wherever the player can actually see it: a toast in
 * the corner (the download usually starts at the main menu, where there is no
 * chat yet) and the log.
 */
public final class ModelDownload {

	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private ModelDownload() {
	}

	public static boolean isRunning() {
		return RUNNING.get();
	}

	/**
	 * Starts a download unless one is already going.
	 *
	 * @param onFinished run on the client thread once the model is in place
	 * @return false if another download is already running
	 */
	public static boolean startOnce(SpeechModels.Entry entry, Runnable onFinished) {
		if (!RUNNING.compareAndSet(false, true)) {
			return false;
		}

		toast(Component.translatable("toast.roleplayersquill.model.downloading"),
				Component.translatable("toast.roleplayersquill.model.size", entry.language(), entry.megabytes()));
		RoleplayersQuill.LOGGER.info("Downloading speech model '{}' ({} MB)", entry.id(), entry.megabytes());

		Thread thread = new Thread(() -> {
			try {
				DownloadState.begin(Component.translatable("roleplayersquill.download.model", entry.language()));
				SpeechModels.download(entry, DownloadState::update);
				toast(Component.translatable("toast.roleplayersquill.model.ready"),
						Component.translatable("toast.roleplayersquill.model.ready.detail", entry.language()));
				Minecraft.getInstance().execute(onFinished);
			} catch (Exception e) {
				RoleplayersQuill.LOGGER.error("Could not download the speech model", e);
				toast(Component.translatable("toast.roleplayersquill.model.failed"),
						Component.literal(String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED));
			} finally {
				DownloadState.end();
				RUNNING.set(false);
			}
		}, "roleplayers-quill-model-download");
		thread.setDaemon(true);
		thread.start();
		return true;
	}

	/**
	 * Fetches the sherpa-onnx native library, once.
	 *
	 * <p>Kept apart from the model download and announced separately, because it
	 * is a different kind of thing: this is executable code, and a player has a
	 * right to see it said plainly rather than folded into "downloading a model".
	 */
	public static boolean startNativesOnce(SherpaNatives.Build build, Runnable onFinished) {
		if (!RUNNING.compareAndSet(false, true)) {
			return false;
		}

		toast(Component.translatable("toast.roleplayersquill.native.downloading"),
				Component.translatable("toast.roleplayersquill.native.size", "sherpa-onnx (" + build.platform() + ")", build.megabytes()));
		RoleplayersQuill.LOGGER.info("Downloading the sherpa-onnx native library for {} ({} MB)",
				build.platform(), build.megabytes());

		Thread thread = new Thread(() -> {
			try {
				DownloadState.begin(Component.translatable("roleplayersquill.download.native"));
				SherpaNatives.download(build, DownloadState::update);
				toast(Component.translatable("toast.roleplayersquill.native.ready"),
						Component.translatable("toast.roleplayersquill.native.ready.detail"));
				Minecraft.getInstance().execute(onFinished);
			} catch (Exception e) {
				RoleplayersQuill.LOGGER.error("Could not download the sherpa-onnx native library", e);
				toast(Component.translatable("toast.roleplayersquill.native.failed"),
						Component.literal(String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED));
			} finally {
				DownloadState.end();
				RUNNING.set(false);
			}
		}, "roleplayers-quill-native-download");
		thread.setDaemon(true);
		thread.start();
		return true;
	}

	private static void toast(Component title, Component description) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.gui.toastManager().addToast(
				new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description)));
	}
}
