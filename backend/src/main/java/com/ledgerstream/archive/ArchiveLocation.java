package com.ledgerstream.archive;

import java.time.Instant;

public record ArchiveLocation(
	String key,
	String uri,
	long sizeBytes,
	String checksumSha256,
	Instant createdAt
) {
}
