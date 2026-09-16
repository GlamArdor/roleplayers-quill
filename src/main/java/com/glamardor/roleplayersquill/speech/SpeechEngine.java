package com.glamardor.roleplayersquill.speech;

/**
 * A recognition back end.
 *
 * <p>Everything around recognition stays the same whichever engine is in use:
 * the resampling down to 16 kHz, the per-speaker queues and worker threads, the
 * silence timeout, the duplicate suppression, the subtitle feed. This interface
 * is the seam where they end and the engine begins, so a second engine is a new
 * implementation rather than a second copy of all that machinery.
 *
 * <p>An engine holds one model in memory and hands out one {@link Stream} per
 * speaker. Streams are used from a single worker thread each; engines are shared
 * across threads and must tolerate it.
 */
public interface SpeechEngine extends AutoCloseable {

	/** The catalogue id of the model this engine loaded. */
	String modelId();

	/** @return a stream for one speaker, or {@code null} if one cannot be made */
	Stream open();

	@Override
	void close();

	/** One speaker's recognition state. */
	interface Stream extends AutoCloseable {

		/**
		 * Feeds one frame of 16 kHz mono audio.
		 *
		 * @return the finished sentence when the engine decided by itself that
		 *         the utterance ended, or {@code null} while it is still going.
		 *         Engines with their own endpoint detection use this; the ones
		 *         without simply never return a value here and leave it to
		 *         {@link #settle()}.
		 */
		String accept(short[] pcm16k);

		/** @return what has been recognised so far, possibly still changing. */
		String partial();

		/**
		 * Ends the utterance: flushes whatever is held and wipes the state, so
		 * the next sentence starts from nothing rather than inheriting the tail
		 * of this one.
		 *
		 * @return the final text, which may be blank
		 */
		String settle();

		@Override
		void close();
	}
}
