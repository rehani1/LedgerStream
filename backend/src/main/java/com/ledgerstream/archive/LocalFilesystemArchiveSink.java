package com.ledgerstream.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

public class LocalFilesystemArchiveSink implements ArchiveSink {

	private final Path rootPath;
	private final Clock clock;

	public LocalFilesystemArchiveSink(Path rootPath, Clock clock) {
		this.rootPath = rootPath.toAbsolutePath().normalize();
		this.clock = clock;
	}

	@Override
	public ArchiveLocation write(String key, byte[] content, String contentType) {
		Path target = rootPath.resolve(key).normalize();
		if (!target.startsWith(rootPath)) {
			throw new ArchiveException("Archive key escapes configured root path");
		}
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content);
			return new ArchiveLocation(
				key,
				target.toUri().toString(),
				content.length,
				ArchiveChecksums.sha256Hex(content),
				Instant.now(clock)
			);
		} catch (IOException ex) {
			throw new ArchiveException("Failed to write archive file", ex);
		}
	}
}
