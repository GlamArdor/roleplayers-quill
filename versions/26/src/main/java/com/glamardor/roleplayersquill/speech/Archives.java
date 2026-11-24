package com.glamardor.roleplayersquill.speech;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Fetching and unpacking, shared by the models and the native library.
 *
 * <p>Two archive formats, because the two projects chose differently: Vosk ships
 * zips, sherpa-onnx ships bzip2 tarballs, which Java cannot read on its own.
 *
 * <p>Every entry is checked against the directory it is meant to land in before
 * anything is written. An archive can name a path like {@code ../../mods/evil.jar},
 * and an unpacker that resolves it blindly will happily write there.
 */
public final class Archives {

	private Archives() {
	}

	/** Told how many bytes have arrived out of how many are expected. */
	public interface Progress {
		/** @param total the size from the server, or -1 when it did not say */
		void update(long read, long total);
	}

	/**
	 * Downloads to {@code target}.
	 *
	 * <p>Progress is reported by bytes rather than whole percents, and at most
	 * ten times a second: a bar that only moves every five percent looks stuck
	 * on a slow connection, which is exactly the impression to avoid.
	 */
	public static void download(String url, Path target, Progress progress) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setConnectTimeout(15_000);
		connection.setReadTimeout(30_000);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("User-Agent", "roleplayers-quill-mod");

		long total = connection.getContentLengthLong();
		long read = 0L;
		long lastReportAt = 0L;

		progress.update(0L, total);
		try (InputStream in = connection.getInputStream();
		     OutputStream out = Files.newOutputStream(target)) {
			byte[] buffer = new byte[64 * 1024];
			int count;
			while ((count = in.read(buffer)) > 0) {
				out.write(buffer, 0, count);
				read += count;
				long now = System.currentTimeMillis();
				if (now - lastReportAt >= 100L) {
					lastReportAt = now;
					progress.update(read, total);
				}
			}
		}
		progress.update(read, total);
	}

	static String sha256(Path file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[64 * 1024];
			int count;
			while ((count = in.read(buffer)) > 0) {
				digest.update(buffer, 0, count);
			}
		}
		StringBuilder hex = new StringBuilder(64);
		for (byte b : digest.digest()) {
			hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return hex.toString();
	}

	/** Told how much has been unpacked so far, for the half of the wait after the download. */
	static void extractZip(Path archive, Path target, Progress progress) throws IOException {
		Path root = target.toAbsolutePath().normalize();
		long written = 0L;
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				Path destination = resolve(root, entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(destination);
				} else {
					Files.createDirectories(destination.getParent());
					written += Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
					progress.update(written, -1L);
				}
				zip.closeEntry();
			}
		}
	}

	static void extractTarBz2(Path archive, Path target, Progress progress) throws IOException {
		Path root = target.toAbsolutePath().normalize();
		long written = 0L;
		try (InputStream raw = Files.newInputStream(archive);
		     BZip2CompressorInputStream bz2 = new BZip2CompressorInputStream(raw, true);
		     TarArchiveInputStream tar = new TarArchiveInputStream(bz2)) {
			TarArchiveEntry entry;
			while ((entry = tar.getNextEntry()) != null) {
				if (!entry.isDirectory() && !entry.isFile()) {
					// Symlinks and device nodes have no business in a model archive.
					continue;
				}
				Path destination = resolve(root, entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(destination);
				} else {
					Files.createDirectories(destination.getParent());
					written += Files.copy(tar, destination, StandardCopyOption.REPLACE_EXISTING);
					progress.update(written, -1L);
				}
			}
		}
	}

	private static Path resolve(Path root, String name) throws IOException {
		Path destination = root.resolve(name).normalize();
		if (!destination.startsWith(root)) {
			throw new IOException("Archive entry outside the target directory: " + name);
		}
		return destination;
	}
}
