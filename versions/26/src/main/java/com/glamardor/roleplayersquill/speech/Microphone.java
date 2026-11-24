package com.glamardor.roleplayersquill.speech;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

/**
 * The microphone, opened directly rather than borrowed.
 *
 * <p>Voice Subtitles takes its audio from Simple Voice Chat, because what it is subtitling is a
 * voice chat. Dictation into a book is not that: it has to work in single player, on a server with
 * no voice mod, and while nobody is talking to anybody. So this opens a recording line itself and
 * asks for nothing from anyone.
 *
 * <p>Sixteen kilohertz mono is what every model here wants. Most devices will give it; the ones
 * that will not are opened at forty-eight and thinned down by the same filter the other mod uses,
 * which is a third of the samples and none of the aliasing.
 */
public final class Microphone implements AutoCloseable {
	private static final int RATE = 16_000;
	private static final int FALLBACK_RATE = 48_000;
	/** A fifth of a second: small enough to feel live, large enough not to spin. */
	private static final int CHUNK_SAMPLES = RATE / 5;

	private final TargetDataLine line;
	private final boolean needsResampling;
	private final Resampler resampler = new Resampler();
	private final byte[] buffer;

	private Microphone(TargetDataLine line, boolean needsResampling) {
		this.line = line;
		this.needsResampling = needsResampling;
		int samples = needsResampling ? CHUNK_SAMPLES * 3 : CHUNK_SAMPLES;
		this.buffer = new byte[samples * 2];
	}

	/**
	 * Opens a recording line.
	 *
	 * @param preferred the mixer name the player chose, or blank for whatever the system offers
	 * @throws LineUnavailableException when there is no microphone, or another program has it
	 */
	public static Microphone open(String preferred) throws LineUnavailableException {
		AudioFormat wanted = format(RATE);
		Mixer.Info chosen = findMixer(preferred, wanted);

		TargetDataLine line = acquire(chosen, wanted);
		if (line != null) {
			line.open(wanted);
			line.start();
			return new Microphone(line, false);
		}

		AudioFormat fallback = format(FALLBACK_RATE);
		line = acquire(findMixer(preferred, fallback), fallback);
		if (line == null) {
			throw new LineUnavailableException("No microphone that can record 16 or 48 kHz mono");
		}
		line.open(fallback);
		line.start();
		RoleplayersQuill.LOGGER.info("Recording at 48 kHz and thinning down: the device would not give 16");
		return new Microphone(line, true);
	}

	@Nullable
	private static TargetDataLine acquire(@Nullable Mixer.Info mixer, AudioFormat format) {
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		try {
			if (mixer != null) {
				Mixer target = AudioSystem.getMixer(mixer);
				if (target.isLineSupported(info)) {
					return (TargetDataLine) target.getLine(info);
				}
			}
			if (AudioSystem.isLineSupported(info)) {
				return (TargetDataLine) AudioSystem.getLine(info);
			}
		} catch (LineUnavailableException | IllegalArgumentException error) {
			RoleplayersQuill.LOGGER.debug("Could not take a line at {} Hz", format.getSampleRate(), error);
		}
		return null;
	}

	@Nullable
	private static Mixer.Info findMixer(String preferred, AudioFormat format) {
		if (preferred == null || preferred.isBlank()) {
			return null;
		}
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		for (Mixer.Info candidate : AudioSystem.getMixerInfo()) {
			if (!candidate.getName().equals(preferred)) {
				continue;
			}
			return AudioSystem.getMixer(candidate).isLineSupported(info) ? candidate : null;
		}
		return null;
	}

	/** Every device that can record, for the settings screen to list. */
	public static List<String> devices() {
		List<String> names = new ArrayList<>();
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format(RATE));
		DataLine.Info fallback = new DataLine.Info(TargetDataLine.class, format(FALLBACK_RATE));
		for (Mixer.Info candidate : AudioSystem.getMixerInfo()) {
			Mixer mixer = AudioSystem.getMixer(candidate);
			if (mixer.isLineSupported(info) || mixer.isLineSupported(fallback)) {
				names.add(candidate.getName());
			}
		}
		return names;
	}

	private static AudioFormat format(int rate) {
		return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, 1, 2, rate, false);
	}

	/**
	 * The next chunk of sound, at 16 kHz, or an empty array when nothing has arrived yet.
	 *
	 * <p>Blocks for as long as the line takes to fill the buffer, so it belongs on a thread of its
	 * own and never on the one drawing the game.
	 */
	public short[] read() {
		int read = line.read(buffer, 0, buffer.length);
		if (read <= 0) {
			return new short[0];
		}
		int samples = read / 2;
		short[] pcm = new short[samples];
		for (int i = 0; i < samples; i++) {
			pcm[i] = (short) ((buffer[i * 2] & 0xFF) | (buffer[i * 2 + 1] << 8));
		}
		return needsResampling ? resampler.process(pcm) : pcm;
	}

	/** How loud the last chunk was, 0 to 1, for drawing a level meter. */
	public static float level(short[] pcm) {
		if (pcm.length == 0) {
			return 0.0f;
		}
		long sum = 0;
		for (short sample : pcm) {
			sum += (long) sample * sample;
		}
		double rms = Math.sqrt((double) sum / pcm.length) / Short.MAX_VALUE;
		// The ear is not linear and neither is a useful meter.
		return (float) Math.min(1.0, Math.sqrt(rms) * 1.6);
	}

	@Override
	public void close() {
		try {
			line.stop();
			line.flush();
		} finally {
			line.close();
		}
	}
}
