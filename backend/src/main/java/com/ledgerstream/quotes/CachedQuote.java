package com.ledgerstream.quotes;

import java.math.BigDecimal;
import java.time.Instant;

public record CachedQuote(
	String symbol,
	Instant timestamp,
	BigDecimal bid,
	BigDecimal ask,
	BigDecimal last,
	Long volume,
	String source
) {
}
