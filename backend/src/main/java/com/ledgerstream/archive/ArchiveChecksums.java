package com.ledgerstream.archive;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class ArchiveChecksums {

	private ArchiveChecksums() {
	}

	static String sha256Hex(byte[] content) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte value : digest) {
				hex.append(String.format("%02x", value));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new ArchiveException("SHA-256 checksum support is unavailable", ex);
		}
	}
}
