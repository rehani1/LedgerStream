package com.ledgerstream.archive;

import java.time.Instant;

public record ArchiveExportResponse(
	String archiveType,
	String key,
	String uri,
	long sizeBytes,
	String checksumSha256,
	int exportedRecords,
	Instant exportedAt
) {

	static ArchiveExportResponse from(String archiveType, ArchiveLocation location, int exportedRecords) {
		return new ArchiveExportResponse(
			archiveType,
			location.key(),
			location.uri(),
			location.sizeBytes(),
			location.checksumSha256(),
			exportedRecords,
			location.createdAt()
		);
	}
}
