package com.ledgerstream.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.TradeOrder;

public record OrderResponse(
	UUID id,
	String symbol,
	OrderSide side,
	OrderType orderType,
	BigDecimal quantity,
	BigDecimal limitPrice,
	OrderStatus status,
	String rejectionReason,
	Instant createdAt,
	Instant updatedAt
) {

	public static OrderResponse from(TradeOrder order) {
		return new OrderResponse(
			order.getId(),
			order.getSymbol().getTicker(),
			order.getSide(),
			order.getOrderType(),
			order.getQuantity(),
			order.getLimitPrice(),
			order.getStatus(),
			order.getRejectionReason(),
			order.getCreatedAt(),
			order.getUpdatedAt()
		);
	}
}
