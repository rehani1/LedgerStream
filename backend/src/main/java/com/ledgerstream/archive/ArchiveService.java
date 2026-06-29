package com.ledgerstream.archive;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerstream.config.properties.ArchiveProperties;
import com.ledgerstream.domain.model.PortfolioSnapshot;
import com.ledgerstream.domain.repository.PortfolioSnapshotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArchiveService {

	private static final String PORTFOLIO_SNAPSHOTS_ARCHIVE = "portfolio_snapshots";
	private static final DateTimeFormatter KEY_TIMESTAMP = DateTimeFormatter
		.ofPattern("yyyyMMdd'T'HHmmss'Z'")
		.withZone(ZoneOffset.UTC);

	private final ArchiveProperties properties;
	private final ArchiveSink archiveSink;
	private final PortfolioSnapshotRepository portfolioSnapshotRepository;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public ArchiveService(
		ArchiveProperties properties,
		ArchiveSink archiveSink,
		PortfolioSnapshotRepository portfolioSnapshotRepository,
		ObjectMapper objectMapper,
		Clock clock
	) {
		this.properties = properties;
		this.archiveSink = archiveSink;
		this.portfolioSnapshotRepository = portfolioSnapshotRepository;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ArchiveExportResponse exportPortfolioSnapshots(LocalDate date) {
		if (!properties.enabled()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archive exports are disabled");
		}
		LocalDate exportDate = date == null ? LocalDate.now(clock) : date;
		Instant from = exportDate.atStartOfDay().toInstant(ZoneOffset.UTC);
		Instant to = exportDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
		List<PortfolioSnapshot> snapshots = portfolioSnapshotRepository
			.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(from, to);

		byte[] content = archivePayload(exportDate, from, to, snapshots);
		ArchiveLocation location = archiveSink.write(archiveKey(exportDate, from, to), content, "application/json");
		return ArchiveExportResponse.from(PORTFOLIO_SNAPSHOTS_ARCHIVE, location, snapshots.size());
	}

	private byte[] archivePayload(LocalDate exportDate, Instant from, Instant to, List<PortfolioSnapshot> snapshots) {
		PortfolioSnapshotArchive archive = new PortfolioSnapshotArchive(
			PORTFOLIO_SNAPSHOTS_ARCHIVE,
			exportDate.toString(),
			timestamp(from),
			timestamp(to),
			timestamp(Instant.now(clock)),
			snapshots.size(),
			snapshots.stream().map(this::archiveRow).toList()
		);
		try {
			return objectMapper.writeValueAsBytes(archive);
		} catch (JsonProcessingException ex) {
			throw new ArchiveException("Failed to serialize portfolio snapshot archive", ex);
		}
	}

	private PortfolioSnapshotArchiveRow archiveRow(PortfolioSnapshot snapshot) {
		return new PortfolioSnapshotArchiveRow(
			snapshot.getId(),
			snapshot.getUser().getId(),
			snapshot.getPortfolio().getId(),
			snapshot.getTotalEquity(),
			snapshot.getCash(),
			snapshot.getMarketValue(),
			snapshot.getGrossExposure(),
			snapshot.getRealizedPnl(),
			snapshot.getUnrealizedPnl(),
			timestamp(snapshot.getCreatedAt())
		);
	}

	private String archiveKey(LocalDate date, Instant from, Instant to) {
		return "portfolio-snapshots/date="
			+ date
			+ "/portfolio-snapshots-"
			+ KEY_TIMESTAMP.format(from)
			+ "-"
			+ KEY_TIMESTAMP.format(to)
			+ ".json";
	}

	private String timestamp(Instant value) {
		return DateTimeFormatter.ISO_INSTANT.format(value);
	}

	private record PortfolioSnapshotArchive(
		String archiveType,
		String date,
		String from,
		String to,
		String exportedAt,
		int recordCount,
		List<PortfolioSnapshotArchiveRow> snapshots
	) {
	}

	private record PortfolioSnapshotArchiveRow(
		UUID id,
		UUID userId,
		UUID portfolioId,
		BigDecimal totalEquity,
		BigDecimal cash,
		BigDecimal marketValue,
		BigDecimal grossExposure,
		BigDecimal realizedPnl,
		BigDecimal unrealizedPnl,
		String createdAt
	) {
	}
}
