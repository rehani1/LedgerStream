package com.ledgerstream.quotes.dto;

import java.util.List;

public record QuoteHistoryResponse(
	String symbol,
	String range,
	int limit,
	List<QuoteResponse> ticks
) {
}
