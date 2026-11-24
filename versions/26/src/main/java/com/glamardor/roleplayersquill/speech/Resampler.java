package com.glamardor.roleplayersquill.speech;

/**
 * Turns Simple Voice Chat's 48 kHz audio into the 16 kHz Vosk expects.
 *
 * <p>Keeping every third sample and throwing the rest away would be simpler, but
 * everything above 8 kHz would fold back down into the speech band as noise and
 * cost real recognition accuracy. So the signal is low-passed first, with a
 * windowed-sinc filter, and only then decimated.
 *
 * <p>One instance per speaker: the filter carries state between frames, and
 * sharing it would smear one voice into another.
 */
public final class Resampler {

	/** 48000 / 16000. */
	private static final int DECIMATION = 3;

	/** Odd, so the filter has a well-defined centre tap. */
	private static final int TAPS = 33;

	/** Cutoff just under the new Nyquist frequency (8 kHz), as a fraction of 48 kHz. */
	private static final double CUTOFF = 7600.0 / 48000.0;

	private static final float[] KERNEL = buildKernel();

	private final float[] history = new float[TAPS];
	private int historyIndex;
	/** Counts input samples, so decimation survives frame boundaries. */
	private int phase;

	/** @return the 16 kHz version of this 48 kHz frame */
	public short[] process(short[] input) {
		short[] output = new short[input.length / DECIMATION + 1];
		int written = 0;

		for (short sample : input) {
			history[historyIndex] = sample;
			historyIndex = (historyIndex + 1) % TAPS;

			if (phase == 0) {
				double sum = 0.0;
				int index = historyIndex;
				for (int tap = 0; tap < TAPS; tap++) {
					sum += history[index] * KERNEL[tap];
					index = (index + 1) % TAPS;
				}
				int value = (int) Math.round(sum);
				output[written++] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
			}
			phase = (phase + 1) % DECIMATION;
		}

		if (written == output.length) {
			return output;
		}
		short[] trimmed = new short[written];
		System.arraycopy(output, 0, trimmed, 0, written);
		return trimmed;
	}

	/** Forgets the previous utterance, so its tail cannot bleed into the next. */
	public void reset() {
		java.util.Arrays.fill(history, 0F);
		historyIndex = 0;
		phase = 0;
	}

	/** Little-endian 16 bit PCM, the byte layout Vosk reads. */
	public static byte[] toBytes(short[] samples) {
		byte[] bytes = new byte[samples.length * 2];
		for (int i = 0; i < samples.length; i++) {
			bytes[i * 2] = (byte) (samples[i] & 0xFF);
			bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
		}
		return bytes;
	}

	/** A sinc low-pass shaped by a Hamming window, normalised to unit gain. */
	private static float[] buildKernel() {
		float[] kernel = new float[TAPS];
		int centre = TAPS / 2;
		double sum = 0.0;

		for (int i = 0; i < TAPS; i++) {
			int n = i - centre;
			double sinc = n == 0
					? 2.0 * CUTOFF
					: Math.sin(2.0 * Math.PI * CUTOFF * n) / (Math.PI * n);
			double window = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (TAPS - 1));
			double value = sinc * window;
			kernel[i] = (float) value;
			sum += value;
		}
		for (int i = 0; i < TAPS; i++) {
			kernel[i] /= (float) sum;
		}
		return kernel;
	}
}
