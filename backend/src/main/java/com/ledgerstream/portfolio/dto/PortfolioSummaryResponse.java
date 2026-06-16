package com.ledgerstream.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioSummaryResponse(
	UUID portfolioId,
	String baseCurrency,
	BigDecimal cash,
	BigDecimal marketValue,
	BigDecimal totalEquity,
	BigDecimal realizedPnl,
	BigDecimal unrealizedPnl,
	int positionsCount,
	int pricedPositionsCount,
	Instant updatedAt
) {
}
