package com.ledgerstream.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioPositionResponse(
	UUID id,
	String symbol,
	BigDecimal quantity,
	BigDecimal avgCost,
	BigDecimal lastPrice,
	BigDecimal valuationPrice,
	String valuationSource,
	BigDecimal marketValue,
	BigDecimal costBasis,
	BigDecimal unrealizedPnl,
	BigDecimal realizedPnl,
	Instant updatedAt
) {
}
