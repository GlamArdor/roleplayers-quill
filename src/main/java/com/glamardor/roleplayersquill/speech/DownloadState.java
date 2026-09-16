package com.glamardor.roleplayersquill.speech;

import net.minecraft.text.Text;

/**
 * What is being downloaded right now, for whoever wants to draw it.
 *
 * <p>A model is a few dozen to a few hundred megabytes and can take minutes on a
 * modest connection. Without something on screen the game looks like it has
 * simply decided not to work, and the natural response is to restart it, which
 * throws the download away and starts it again.
 *
 * <p>Written from the download thread, read from the render thread, hence the
 * volatile fields and the immutable snapshot.
 */
public final class DownloadState {

	private static volatile Text label = Text.empty();
	private static volatile long read;
	private static volatile long total;
	private static volatile long startedAt;
	private static volatile boolean active;
	/** True once the bytes are in and the slow part starts: checking and unpacking. */
	private static volatile boolean installing;
	/** True when nothing is being fetched at all: the bytes were inside the mod. */
	private static volatile boolean unpacking;

	private DownloadState() {
	}

	/** One reading of the state, so the renderer sees consistent numbers. */
	public record Snapshot(Text label, long read, long total, long elapsedMillis, boolean installing,
	                       boolean unpacking) {

		/** @return 0–1, or -1 when the server did not say how big the file is */
		public float fraction() {
			return total > 0L ? Math.min(1F, read / (float) total) : -1F;
		}

		public int percent() {
			return total > 0L ? (int) Math.min(100L, read * 100L / total) : 0;
		}

		/** @return bytes per second so far, or 0 before anything has arrived */
		public long bytesPerSecond() {
			return elapsedMillis > 500L ? read * 1000L / elapsedMillis : 0L;
		}

		/** @return seconds left at the current rate, or -1 when it cannot be told */
		public long secondsLeft() {
			long rate = bytesPerSecond();
			if (rate <= 0L || total <= 0L || read >= total) {
				return -1L;
			}
			return (total - read) / rate;
		}
	}

	public static void begin(Text what) {
		label = what;
		read = 0L;
		total = -1L;
		startedAt = System.currentTimeMillis();
		active = true;
		installing = false;
		unpacking = false;
	}

	/**
	 * The same wait, with nothing coming over the network: the full build copying
	 * what it was packed with out of its own jar.
	 *
	 * <p>Told apart from a download because it is worth saying which it is. A bar
	 * that reads "downloading" on a machine that is not downloading anything is a
	 * bar that will be asked about.
	 */
	public static void unpacking(Text what, long bytes) {
		begin(what);
		unpacking = true;
		total = bytes;
	}

	/**
	 * The download is done and the unpacking has begun.
	 *
	 * <p>Worth saying out loud: a checksum over half a gigabyte and unpacking it
	 * take long enough that a bar sitting at 100 % looks like a bar that has
	 * stopped, and the natural response to that is to close the game.
	 */
	public static void installing() {
		installing = true;
		read = 0L;
		total = -1L;
		startedAt = System.currentTimeMillis();
	}

	public static void update(long bytesRead, long bytesTotal) {
		read = bytesRead;
		total = bytesTotal;
	}

	public static void end() {
		active = false;
	}

	public static boolean isActive() {
		return active;
	}

	/** @return the current state, or {@code null} when nothing is downloading */
	public static Snapshot snapshot() {
		if (!active) {
			return null;
		}
		return new Snapshot(label, read, total, System.currentTimeMillis() - startedAt, installing, unpacking);
	}
}
