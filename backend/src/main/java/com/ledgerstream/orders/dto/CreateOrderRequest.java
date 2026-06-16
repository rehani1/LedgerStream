package com.ledgerstream.orders.dto;

import java.math.BigDecimal;

import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
	@NotBlank String symbol,
	@NotNull OrderSide side,
	@NotNull OrderType orderType,
	@NotNull @DecimalMin(value = "0.000001") BigDecimal quantity,
	@DecimalMin(value = "0.000001") BigDecimal limitPrice
) {
}
