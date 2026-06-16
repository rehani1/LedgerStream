package com.ledgerstream.orders;

import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.events.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderExecutionConsumerTest {

	@Mock
	private OrderExecutionService orderExecutionService;

	private OrderExecutionConsumer consumer;

	@BeforeEach
	void setUp() {
		consumer = new OrderExecutionConsumer(orderExecutionService);
	}

	@Test
	void receiveDelegatesToExecutionService() {
		OrderCreatedEvent event = new OrderCreatedEvent(
			UUID.randomUUID(),
			UUID.randomUUID(),
			UUID.randomUUID(),
			"AAPL",
			OrderSide.BUY,
			OrderType.MARKET,
			new BigDecimal("10.000000"),
			null,
			Instant.parse("2026-01-02T14:35:00Z")
		);

		consumer.receive(event);

		verify(orderExecutionService).execute(event);
	}
}
