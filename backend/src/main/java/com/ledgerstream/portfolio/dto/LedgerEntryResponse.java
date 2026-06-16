package com.ledgerstream.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.ledgerstream.domain.model.LedgerEntry;
import com.ledgerstream.domain.model.LedgerEntryType;

public record LedgerEntryResponse(
	UUID id,
	LedgerEntryType entryType,
	BigDecimal cashDelta,
	String symbol,
	BigDecimal quantityDelta,
	BigDecimal price,
	UUID orderId,
	UUID fillId,
	Instant createdAt,
	Map<String, Object> metadata
) {

	public static LedgerEntryResponse from(LedgerEntry entry) {
		return new LedgerEntryResponse(
			entry.getId(),
			entry.getEntryType(),
			entry.getCashDelta(),
			entry.getSymbol() == null ? null : entry.getSymbol().getTicker(),
			entry.getQuantityDelta(),
			entry.getPrice(),
			entry.getOrder() == null ? null : entry.getOrder().getId(),
			entry.getFill() == null ? null : entry.getFill().getId(),
			entry.getCreatedAt(),
			entry.getMetadata()
		);
	}
}
