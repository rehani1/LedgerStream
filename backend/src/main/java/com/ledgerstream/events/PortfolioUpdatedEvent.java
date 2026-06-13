package com.ledgerstream.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioUpdatedEvent(
	UUID eventId,
	UUID userId,
	UUID portfolioId,
	BigDecimal totalEquity,
	BigDecimal cash,
	Instant updatedAt
) {
}
