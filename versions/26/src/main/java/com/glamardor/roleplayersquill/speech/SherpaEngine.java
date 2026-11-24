package com.glamardor.roleplayersquill.speech;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.k2fsa.sherpa.onnx.EndpointConfig;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineToneCtcModelConfig;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * sherpa-onnx, the streaming alternative to Vosk.
 *
 * <p>ONNX Runtime underneath, so a model is a few hundred megabytes at most and
 * is ready seconds after it is asked for rather than the better part of a
 * minute. Streaming like Vosk, so words still appear as they are said.
 *
 * <p>Two kinds of model are understood, told apart by what is in the folder: a
 * zipformer transducer, which is an encoder, a decoder and a joiner, or a T-one
 * CTC model, which is a single file. Both come with a {@code tokens.txt}.
 */
final class SherpaEngine implements SpeechEngine {

	/** Recognition runs at 16 kHz, like everything else here. */
	private static final int RATE = 16_000;

	private final String modelId;
	private final OnlineRecognizer recognizer;

	private SherpaEngine(String modelId, OnlineRecognizer recognizer) {
		this.modelId = modelId;
		this.recognizer = recognizer;
	}

	/**
	 * Loads a model. Quick by Vosk standards, but still not render-thread work.
	 *
	 * @throws IllegalStateException if the native library is not loaded, which
	 *                               the caller is expected to have arranged
	 */
	static SherpaEngine load(SpeechModels.Entry entry) throws IOException {
		if (!SherpaNatives.load()) {
			throw new IllegalStateException("sherpa-onnx native library unavailable: " + SherpaNatives.failure());
		}
		Path dir = SpeechModels.pathOf(entry);
		Path tokens = dir.resolve("tokens.txt");
		if (!Files.isRegularFile(tokens)) {
			throw new IOException("No tokens.txt in " + dir);
		}

		OnlineModelConfig.Builder model = OnlineModelConfig.builder()
				.setTokens(tokens.toString())
				// One thread per speaker already; more would fight the game for
				// cores it needs to draw frames.
				.setNumThreads(1)
				.setDebug(false);

		Path single = find(dir, "model");
		if (single != null) {
			model.setToneCtc(OnlineToneCtcModelConfig.builder().setModel(single.toString()).build());
		} else {
			Path encoder = require(dir, "encoder");
			Path decoder = require(dir, "decoder");
			Path joiner = require(dir, "joiner");
			model.setTransducer(OnlineTransducerModelConfig.builder()
					.setEncoder(encoder.toString())
					.setDecoder(decoder.toString())
					.setJoiner(joiner.toString())
					.build());
		}

		OnlineRecognizerConfig config = OnlineRecognizerConfig.builder()
				.setOnlineModelConfig(model.build())
				.setFeatureConfig(FeatureConfig.builder().setSampleRate(RATE).setFeatureDim(80).build())
				// Its own endpoint detection is better than a timer: it knows the
				// difference between a pause for breath and the end of a thought.
				// The silence timeout upstream stays as a backstop.
				.setEndpointConfig(EndpointConfig.builder().build())
				.setEnableEndpoint(true)
				.setDecodingMethod("greedy_search")
				.build();

		OnlineRecognizer recognizer = new OnlineRecognizer(config);
		RoleplayersQuill.LOGGER.info("sherpa-onnx model '{}' loaded from {}", entry.id(), dir);
		return new SherpaEngine(entry.id(), recognizer);
	}

	/**
	 * The one {@code .onnx} file whose name starts with {@code prefix}.
	 *
	 * <p>Found by looking rather than by exact name: the same model ships as
	 * {@code encoder.onnx} and {@code encoder.int8.onnx} depending on whether it
	 * is the quantised build.
	 */
	static Path findModelFile(Path dir, String prefix) throws IOException {
		Path found = find(dir, prefix);
		return found == null ? null : verify(found);
	}

	static Path requireModelFile(Path dir, String prefix) throws IOException {
		return verify(require(dir, prefix));
	}

	/**
	 * Reads enough of a model file to be sure it can be read at all.
	 *
	 * <p>Because the library that reads it properly cannot be argued with. Handed
	 * a file it cannot open, sherpa-onnx throws a C++ exception, and a C++
	 * exception crossing back into Java takes the whole game down: no crash
	 * report, no message, nothing but a line saying the process died in native
	 * code. Two players lost their game to it, both within a third of a second of
	 * the library loading – far too soon to have read two hundred megabytes, so
	 * the file was never opened at all.
	 *
	 * <p>Antivirus software is the usual reason. A two-hundred-megabyte file that
	 * has just appeared on disk is exactly what it wants to look at, and while it
	 * is looking, everything else trying to read that file is turned away.
	 *
	 * <p>So the whole file is read here first, start to end, and thrown away. It
	 * costs a second of disk on a background thread and it settles the question
	 * before the library is involved: either the read succeeds, in which case the
	 * scanner has finished with it and the library will be let in too, or it
	 * fails here, in Java, where a failure is a caption that never appears rather
	 * than a game that dies. Failing that, it waits and tries again, because a
	 * scan that is under way finishes on its own.
	 *
	 * <p>This is why the mod does not ask anybody to make an exception for it in
	 * their antivirus. Being told to switch off the thing protecting your machine
	 * is what malware asks for, and a mod that needs it asked for is a mod with a
	 * bug in it.
	 *
	 * @return the same path, once it has proved readable
	 * @throws IOException if it cannot be read, or does not look like a model
	 */
	private static Path verify(Path file) throws IOException {
		long size = Files.size(file);
		// Low on purpose. The smallest real piece of a model here is a joiner of
		// a megabyte and a half, and the point of this number is to catch a file
		// that is empty or a few kilobytes of nothing, not to judge how big a
		// model ought to be.
		if (size < 64L * 1024L) {
			throw new IOException("Model file " + file.getFileName() + " is only " + size
					+ " bytes; it did not finish downloading");
		}

		IOException last = null;
		for (int attempt = 1; attempt <= READ_ATTEMPTS; attempt++) {
			try {
				long began = System.currentTimeMillis();
				readWhole(file);
				RoleplayersQuill.LOGGER.info("Model file {} read in full, {} MB in {} ms",
						file.getFileName(), size / 1048576L, System.currentTimeMillis() - began);
				return file;
			} catch (IOException e) {
				last = e;
				RoleplayersQuill.LOGGER.warn("Could not read {} (attempt {} of {}): {}",
						file.getFileName(), attempt, READ_ATTEMPTS, e.getMessage());
				try {
					Thread.sleep(READ_RETRY_MS);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		throw new IOException("Model file " + file + " could not be read after " + READ_ATTEMPTS
				+ " attempts: " + (last == null ? "?" : last.getMessage()), last);
	}

	/** How many times a file is given the chance to become readable. */
	private static final int READ_ATTEMPTS = 4;

	/** How long to wait between those, while a scanner finishes with it. */
	private static final long READ_RETRY_MS = 3000L;

	private static void readWhole(Path file) throws IOException {
		try (java.io.InputStream in = new java.io.BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
			// Every ONNX model is a protobuf beginning with its version field,
			// which is field one as a varint: byte 0x08.
			int first = in.read();
			if (first != 0x08) {
				throw new IOException("does not begin like an ONNX model (first byte 0x"
						+ Integer.toHexString(first) + "), so it is damaged rather than merely busy");
			}
			byte[] buffer = new byte[1 << 16];
			while (in.read(buffer) > 0) {
				// Reading is the whole point; what was read is of no interest.
			}
		}
	}

	private static Path find(Path dir, String prefix) throws IOException {
		// Fully qualified: this class inherits SpeechEngine.Stream, which would
		// otherwise shadow the one from java.util.stream.
		try (java.util.stream.Stream<Path> files = Files.list(dir)) {
			List<Path> matches = files
					.filter(p -> {
						String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
						return name.startsWith(prefix) && name.endsWith(".onnx");
					})
					.sorted()
					.toList();
			return matches.isEmpty() ? null : matches.get(0);
		}
	}

	private static Path require(Path dir, String prefix) throws IOException {
		Path found = find(dir, prefix);
		if (found == null) {
			throw new IOException("No " + prefix + "*.onnx in " + dir);
		}
		return found;
	}

	@Override
	public String modelId() {
		return modelId;
	}

	@Override
	public SpeechEngine.Stream open() {
		try {
			return new SherpaStream(recognizer.createStream());
		} catch (Throwable t) {
			RoleplayersQuill.LOGGER.error("Could not start a sherpa-onnx stream", t);
			return null;
		}
	}

	@Override
	public void close() {
		try {
			recognizer.release();
		} catch (Throwable ignored) {
		}
	}

	private final class SherpaStream implements SpeechEngine.Stream {

		private final com.k2fsa.sherpa.onnx.OnlineStream stream;

		SherpaStream(com.k2fsa.sherpa.onnx.OnlineStream stream) {
			this.stream = stream;
		}

		@Override
		public String accept(short[] pcm16k) {
			stream.acceptWaveform(toFloat(pcm16k), RATE);
			while (recognizer.isReady(stream)) {
				recognizer.decode(stream);
			}
			if (!recognizer.isEndpoint(stream)) {
				return null;
			}
			// An endpoint means the speaker finished a thought, not that they
			// stopped talking; the stream carries straight on into the next one.
			String text = recognizer.getResult(stream).getText().trim();
			recognizer.reset(stream);
			return text;
		}

		@Override
		public String partial() {
			return recognizer.getResult(stream).getText().trim();
		}

		@Override
		public String settle() {
			try {
				// Half a second of silence first: the encoder needs audio past
				// the last word before it will commit to it, and without this
				// the final word of a sentence is regularly the one that goes
				// missing. Measured on T-one, which needs the most of it.
				stream.acceptWaveform(new float[RATE / 2], RATE);
				while (recognizer.isReady(stream)) {
					recognizer.decode(stream);
				}
				String text = recognizer.getResult(stream).getText().trim();
				recognizer.reset(stream);
				return text;
			} catch (Throwable t) {
				return "";
			}
		}

		@Override
		public void close() {
			try {
				stream.release();
			} catch (Throwable ignored) {
			}
		}

		/** sherpa-onnx wants samples as floats between -1 and 1. */
		private static float[] toFloat(short[] pcm) {
			float[] out = new float[pcm.length];
			for (int i = 0; i < pcm.length; i++) {
				out[i] = pcm[i] / 32768F;
			}
			return out;
		}
	}
}
