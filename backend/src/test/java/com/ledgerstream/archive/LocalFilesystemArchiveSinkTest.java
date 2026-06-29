package com.ledgerstream.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesystemArchiveSinkTest {

	private static final Instant NOW = Instant.parse("2026-01-03T01:00:00Z");

	@TempDir
	private Path tempDir;

	@Test
	void writeStoresArchiveUnderConfiguredRoot() throws Exception {
		LocalFilesystemArchiveSink sink = new LocalFilesystemArchiveSink(tempDir, Clock.fixed(NOW, ZoneOffset.UTC));

		ArchiveLocation location = sink.write("portfolio-snapshots/date=2026-01-02/archive.json", "{}".getBytes(StandardCharsets.UTF_8), "application/json");

		Path target = tempDir.resolve("portfolio-snapshots/date=2026-01-02/archive.json");
		assertThat(Files.readString(target)).isEqualTo("{}");
		assertThat(location.uri()).isEqualTo(target.toUri().toString());
		assertThat(location.sizeBytes()).isEqualTo(2);
		assertThat(location.checksumSha256()).isEqualTo(ArchiveChecksums.sha256Hex("{}".getBytes(StandardCharsets.UTF_8)));
		assertThat(location.createdAt()).isEqualTo(NOW);
	}

	@Test
	void writeRejectsPathTraversalKeys() {
		LocalFilesystemArchiveSink sink = new LocalFilesystemArchiveSink(tempDir, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatThrownBy(() -> sink.write("../escape.json", "{}".getBytes(StandardCharsets.UTF_8), "application/json"))
			.isInstanceOf(ArchiveException.class)
			.hasMessage("Archive key escapes configured root path");
	}
}
