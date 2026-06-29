package com.ledgerstream.archive;

public class ArchiveException extends RuntimeException {

	public ArchiveException(String message) {
		super(message);
	}

	public ArchiveException(String message, Throwable cause) {
		super(message, cause);
	}
}
