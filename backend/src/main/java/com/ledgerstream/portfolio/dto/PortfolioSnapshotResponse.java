package com.ledgerstream.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ledgerstream.domain.model.PortfolioSnapshot;

public record PortfolioSnapshotResponse(
	UUID id,
	UUID portfolioId,
	BigDecimal totalEquity,
	BigDecimal cash,
	BigDecimal marketValue,
	BigDecimal grossExposure,
	BigDecimal realizedPnl,
	BigDecimal unrealizedPnl,
	Instant createdAt
) {

	public static PortfolioSnapshotResponse from(PortfolioSnapshot snapshot) {
		return new PortfolioSnapshotResponse(
			snapshot.getId(),
			snapshot.getPortfolio().getId(),
			snapshot.getTotalEquity(),
			snapshot.getCash(),
			snapshot.getMarketValue(),
			snapshot.getGrossExposure(),
			snapshot.getRealizedPnl(),
			snapshot.getUnrealizedPnl(),
			snapshot.getCreatedAt()
		);
	}
}
