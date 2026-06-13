package com.ledgerstream.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ledgerstream.domain.model.OrderSide;

public record OrderFilledEvent(
	UUID eventId,
	UUID orderId,
	UUID fillId,
	UUID userId,
	String symbol,
	OrderSide side,
	BigDecimal quantity,
	BigDecimal price,
	BigDecimal fee,
	Instant filledAt
) {
}
