package com.ledgerstream.risk.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ledgerstream.domain.model.RiskSnapshot;

public record RiskSnapshotResponse(
	UUID id,
	BigDecimal totalEquity,
	BigDecimal cash,
	BigDecimal grossExposure,
	BigDecimal largestPositionPct,
	BigDecimal unrealizedPnl,
	Instant createdAt
) {

	public static RiskSnapshotResponse from(RiskSnapshot snapshot) {
		return new RiskSnapshotResponse(
			snapshot.getId(),
			snapshot.getTotalEquity(),
			snapshot.getCash(),
			snapshot.getGrossExposure(),
			snapshot.getLargestPositionPct(),
			snapshot.getUnrealizedPnl(),
			snapshot.getCreatedAt()
		);
	}
}
