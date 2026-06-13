package com.ledgerstream.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketTickEvent(
	UUID eventId,
	String symbol,
	Instant timestamp,
	BigDecimal bid,
	BigDecimal ask,
	BigDecimal last,
	Long volume,
	String source
) {
}
