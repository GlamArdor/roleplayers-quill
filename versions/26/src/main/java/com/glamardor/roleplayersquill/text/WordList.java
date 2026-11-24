package com.glamardor.roleplayersquill.text;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Every word of a language, in twelve megabytes, answering one question: is this a word.
 *
 * <p>A Russian spelling list is a million and a half forms – Zaliznyak's tables written out – and
 * keeping them as strings would cost a hundred and fifty megabytes of a game's heap to answer a
 * question that never needs the string back. So each word is hashed to eight bytes and the hashes
 * are sorted; looking one up is a binary search, and the words themselves are thrown away with the
 * file they were read from.
 *
 * <p>Two words sharing a hash would mean a misspelling quietly accepted. With sixty-four bits and a
 * million and a half words the chance of even one such pair anywhere in the list is about one in ten
 * million, and what it costs when it happens is one wrong word not underlined.
 *
 * <p>Suggestions are not lost by this, which is the part worth saying twice: they are not looked up
 * in the list, they are <em>made</em> – every word one keystroke away from what was typed is
 * generated and then asked about here. A membership test is all that needs.
 */
public final class WordList {
	/** What a built index starts with, so half a file or somebody else's is not read as one. */
	private static final int MAGIC = 0x51535044; // "QSPD"
	private static final int VERSION = 1;

	private final long[] hashes;

	private WordList(long[] hashes) {
		this.hashes = hashes;
	}

	public int size() {
		return hashes.length;
	}

	public boolean knows(String lowercased) {
		return Arrays.binarySearch(hashes, hash(lowercased)) >= 0;
	}

	/**
	 * The hash a word is remembered by.
	 *
	 * <p>FNV-1a over the characters, then a final mixing step. FNV alone leaves the low bits of
	 * short words too close together, and the low bits are exactly what a sorted array leans on.
	 */
	private static long hash(String word) {
		long h = 0xcbf29ce484222325L;
		for (int i = 0; i < word.length(); i++) {
			h ^= word.charAt(i);
			h *= 0x100000001b3L;
		}
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 29;
		return h;
	}

	// ---- building ---------------------------------------------------------------------------------

	/**
	 * Reads a downloaded word list and writes the index beside it.
	 *
	 * <p>The list is read as bytes rather than through a charset. One of the two is Windows-1251 –
	 * that is how the Russian one is published – and a game's runtime is not required to carry every
	 * charset the desktop does. Two dozen lines of arithmetic here are cheaper than finding out on
	 * somebody else's machine that {@code windows-1251} is not installed.
	 *
	 * @param cyrillic whether the bytes are Windows-1251 rather than ASCII
	 */
	public static WordList build(Path list, Path index, boolean cyrillic) throws IOException {
		long[] hashes = new long[1 << 20];
		int count = 0;
		StringBuilder word = new StringBuilder(32);

		try (InputStream raw = new BufferedInputStream(Files.newInputStream(list), 1 << 16)) {
			int read;
			while ((read = raw.read()) >= 0) {
				if (read == '\n' || read == '\r') {
					if (word.length() >= 2) {
						if (count == hashes.length) {
							hashes = Arrays.copyOf(hashes, count * 2);
						}
						hashes[count++] = hash(word.toString());
					}
					word.setLength(0);
					continue;
				}
				char c = cyrillic ? fromCp1251(read) : fromAscii(read);
				// Anything that is not a letter of the language ends the word rather than joining it:
				// the lists carry a few oddities – full stops inside abbreviations, stray spaces – and
				// a hyphen, which words like "кто-то" are entitled to.
				if (c == 0) {
					word.setLength(0);
					// Skip to the end of the line: half a word is worse than none.
					while ((read = raw.read()) >= 0 && read != '\n') {
						// nothing
					}
					continue;
				}
				word.append(c);
			}
		}
		if (word.length() >= 2) {
			if (count == hashes.length) {
				hashes = Arrays.copyOf(hashes, count + 1);
			}
			hashes[count++] = hash(word.toString());
		}

		long[] sorted = Arrays.copyOf(hashes, count);
		Arrays.sort(sorted);
		sorted = dedupe(sorted);
		write(sorted, index);
		return new WordList(sorted);
	}

	/**
	 * One byte of the Russian list as the letter it stands for, already in lower case.
	 *
	 * <p>Zero for anything that is not a letter or a hyphen, which is this method's way of saying
	 * that whatever is being read is not a word.
	 */
	private static char fromCp1251(int b) {
		if (b >= 0xC0 && b <= 0xDF) {
			return (char) (0x430 + (b - 0xC0));
		}
		if (b >= 0xE0 && b <= 0xFF) {
			return (char) (0x430 + (b - 0xE0));
		}
		if (b == 0xA8 || b == 0xB8) {
			return 'ё';
		}
		if (b == '-') {
			return '-';
		}
		if (b >= 'A' && b <= 'Z') {
			return (char) (b - 'A' + 'a');
		}
		if (b >= 'a' && b <= 'z') {
			return (char) b;
		}
		return 0;
	}

	/** The same for the English list, which is plain ASCII and already in lower case. */
	private static char fromAscii(int b) {
		if (b >= 'A' && b <= 'Z') {
			return (char) (b - 'A' + 'a');
		}
		if (b >= 'a' && b <= 'z' || b == '-' || b == '\'') {
			return (char) b;
		}
		return 0;
	}

	private static long[] dedupe(long[] sorted) {
		if (sorted.length == 0) {
			return sorted;
		}
		int kept = 1;
		for (int i = 1; i < sorted.length; i++) {
			if (sorted[i] != sorted[kept - 1]) {
				sorted[kept++] = sorted[i];
			}
		}
		return Arrays.copyOf(sorted, kept);
	}

	private static void write(long[] hashes, Path index) throws IOException {
		Files.createDirectories(index.getParent());
		try (OutputStream out = Files.newOutputStream(index);
		     DataOutputStream data = new DataOutputStream(new java.io.BufferedOutputStream(out, 1 << 16))) {
			data.writeInt(MAGIC);
			data.writeInt(VERSION);
			data.writeInt(hashes.length);
			for (long hash : hashes) {
				data.writeLong(hash);
			}
		}
	}

	/** An index written earlier, or null when there is none to read or it is not one of ours. */
	public static WordList read(Path index) throws IOException {
		if (!Files.isRegularFile(index)) {
			return null;
		}
		byte[] bytes = Files.readAllBytes(index);
		if (bytes.length < 12) {
			return null;
		}
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
		if (buffer.getInt() != MAGIC || buffer.getInt() != VERSION) {
			return null;
		}
		int count = buffer.getInt();
		if (count < 0 || bytes.length < 12 + (long) count * 8) {
			return null;
		}
		long[] hashes = new long[count];
		LongBuffer longs = buffer.asLongBuffer();
		longs.get(hashes);
		return new WordList(hashes);
	}
}
