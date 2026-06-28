package com.ledgerstream.events;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	private KafkaEventPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new KafkaEventPublisher(kafkaTemplate);
	}

	@Test
	void publishOrderCreatedUsesOrderTopicAndOrderIdKey() {
		UUID orderId = UUID.randomUUID();
		OrderCreatedEvent event = new OrderCreatedEvent(
			UUID.randomUUID(),
			orderId,
			UUID.randomUUID(),
			"AAPL",
			OrderSide.BUY,
			OrderType.MARKET,
			new BigDecimal("10.000000"),
			null,
			"request-1",
			Instant.parse("2026-01-01T14:30:00Z")
		);
		when(kafkaTemplate.send(EventTopics.ORDER_CREATED, orderId.toString(), event))
			.thenReturn(CompletableFuture.completedFuture(null));

		publisher.publishOrderCreated(event).join();

		verify(kafkaTemplate).send(EventTopics.ORDER_CREATED, orderId.toString(), event);
	}

	@Test
	void publishAuditEventUsesActionKeyWhenUserIsMissing() {
		AuditEventPayload event = new AuditEventPayload(
			UUID.randomUUID(),
			null,
			"LOGIN_FAILED",
			"request-1",
			java.util.Map.of("reason", "invalid_credentials"),
			Instant.parse("2026-01-01T14:30:00Z")
		);
		when(kafkaTemplate.send(EventTopics.AUDIT_EVENT, "LOGIN_FAILED", event))
			.thenReturn(CompletableFuture.completedFuture(null));

		publisher.publishAuditEvent(event).join();

		verify(kafkaTemplate).send(EventTopics.AUDIT_EVENT, "LOGIN_FAILED", event);
	}
}
