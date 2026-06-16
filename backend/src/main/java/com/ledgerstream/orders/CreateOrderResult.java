package com.ledgerstream.orders;

import com.ledgerstream.orders.dto.OrderResponse;

public record CreateOrderResult(OrderResponse order, boolean created) {
}
