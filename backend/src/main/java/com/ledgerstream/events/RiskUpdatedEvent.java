package com.ledgerstream.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RiskUpdatedEvent(
	UUID eventId,
	UUID userId,
	BigDecimal totalEquity,
	BigDecimal grossExposure,
	BigDecimal largestPositionPct,
	BigDecimal unrealizedPnl,
	Instant createdAt
) {
}
