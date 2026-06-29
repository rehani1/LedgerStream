package com.ledgerstream.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerstream.config.properties.ArchiveProperties;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.PortfolioSnapshot;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.PortfolioSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-03T01:00:00Z");
	private static final LocalDate EXPORT_DATE = LocalDate.parse("2026-01-02");
	private static final Instant FROM = Instant.parse("2026-01-02T00:00:00Z");
	private static final Instant TO = Instant.parse("2026-01-03T00:00:00Z");

	@Mock
	private PortfolioSnapshotRepository portfolioSnapshotRepository;

	private ObjectMapper objectMapper;
	private FakeArchiveSink archiveSink;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		archiveSink = new FakeArchiveSink();
	}

	@Test
	void exportPortfolioSnapshotsWritesJsonArchiveForDate() throws Exception {
		PortfolioSnapshot snapshot = snapshot();
		when(portfolioSnapshotRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(FROM, TO))
			.thenReturn(List.of(snapshot));
		ArchiveService archiveService = new ArchiveService(
			enabledProperties(),
			archiveSink,
			portfolioSnapshotRepository,
			objectMapper,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);

		ArchiveExportResponse response = archiveService.exportPortfolioSnapshots(EXPORT_DATE);

		assertThat(response.archiveType()).isEqualTo("portfolio_snapshots");
		assertThat(response.exportedRecords()).isEqualTo(1);
		assertThat(response.key()).isEqualTo("portfolio-snapshots/date=2026-01-02/portfolio-snapshots-20260102T000000Z-20260103T000000Z.json");
		assertThat(response.uri()).isEqualTo("memory://" + response.key());
		assertThat(response.sizeBytes()).isGreaterThan(0);
		assertThat(response.checksumSha256()).hasSize(64);
		assertThat(response.exportedAt()).isEqualTo(NOW);
		assertThat(archiveSink.contentType).isEqualTo("application/json");

		JsonNode archive = objectMapper.readTree(archiveSink.content);
		assertThat(archive.get("archiveType").asText()).isEqualTo("portfolio_snapshots");
		assertThat(archive.get("date").asText()).isEqualTo("2026-01-02");
		assertThat(archive.get("recordCount").asInt()).isEqualTo(1);
		assertThat(archive.get("snapshots").get(0).get("userId").asText()).isEqualTo(snapshot.getUser().getId().toString());
		assertThat(archive.get("snapshots").get(0).get("totalEquity").decimalValue()).isEqualByComparingTo("1600.00");
		verify(portfolioSnapshotRepository).findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(FROM, TO);
	}

	@Test
	void exportPortfolioSnapshotsRejectsWhenArchiveIsDisabled() {
		ArchiveService archiveService = new ArchiveService(
			disabledProperties(),
			archiveSink,
			portfolioSnapshotRepository,
			objectMapper,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);

		assertThatThrownBy(() -> archiveService.exportPortfolioSnapshots(EXPORT_DATE))
			.isInstanceOf(ResponseStatusException.class)
			.extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
			.isEqualTo(HttpStatus.CONFLICT);
		verifyNoInteractions(portfolioSnapshotRepository);
		assertThat(archiveSink.content).isNull();
	}

	private ArchiveProperties enabledProperties() {
		return new ArchiveProperties(
			true,
			ArchiveProperties.Backend.FILESYSTEM,
			new ArchiveProperties.Local(Path.of("build/test-archives")),
			new ArchiveProperties.HttpPut(null, null)
		);
	}

	private ArchiveProperties disabledProperties() {
		return new ArchiveProperties(
			false,
			ArchiveProperties.Backend.FILESYSTEM,
			new ArchiveProperties.Local(Path.of("build/test-archives")),
			new ArchiveProperties.HttpPut(null, null)
		);
	}

	private PortfolioSnapshot snapshot() {
		User user = new User();
		user.setId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
		user.setEmail("user@example.com");
		user.setPasswordHash("hash");
		user.setRole(UserRole.USER);

		Portfolio portfolio = new Portfolio();
		portfolio.setId(UUID.fromString("00000000-0000-0000-0000-000000000201"));
		portfolio.setUser(user);
		portfolio.setCashBalance(new BigDecimal("1000.00"));
		portfolio.setBaseCurrency("USD");

		PortfolioSnapshot snapshot = new PortfolioSnapshot();
		snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000301"));
		snapshot.setUser(user);
		snapshot.setPortfolio(portfolio);
		snapshot.setTotalEquity(new BigDecimal("1600.00"));
		snapshot.setCash(new BigDecimal("1000.00"));
		snapshot.setMarketValue(new BigDecimal("600.00"));
		snapshot.setGrossExposure(new BigDecimal("600.00"));
		snapshot.setRealizedPnl(new BigDecimal("12.34"));
		snapshot.setUnrealizedPnl(new BigDecimal("100.00"));
		snapshot.setCreatedAt(Instant.parse("2026-01-02T14:35:00Z"));
		return snapshot;
	}

	private static class FakeArchiveSink implements ArchiveSink {
		private byte[] content;
		private String contentType;

		@Override
		public ArchiveLocation write(String key, byte[] content, String contentType) {
			this.content = content;
			this.contentType = contentType;
			return new ArchiveLocation(
				key,
				"memory://" + key,
				content.length,
				ArchiveChecksums.sha256Hex(content),
				NOW
			);
		}

		@Override
		public String toString() {
			return content == null ? "" : new String(content, StandardCharsets.UTF_8);
		}
	}
}
