package com.ledgerstream.quotes.dto;

import java.time.Instant;
import java.util.List;

public record QuoteStreamReadyResponse(
	List<String> symbols,
	Instant connectedAt
) {
}
