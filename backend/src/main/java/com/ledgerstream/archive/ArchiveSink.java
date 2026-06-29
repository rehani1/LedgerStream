package com.ledgerstream.archive;

public interface ArchiveSink {

	ArchiveLocation write(String key, byte[] content, String contentType);
}
