package com.ledgerstream.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderType;

public record OrderCreatedEvent(
	UUID eventId,
	UUID orderId,
	UUID userId,
	String symbol,
	OrderSide side,
	OrderType orderType,
	BigDecimal quantity,
	BigDecimal limitPrice,
	Instant createdAt
) {
}
