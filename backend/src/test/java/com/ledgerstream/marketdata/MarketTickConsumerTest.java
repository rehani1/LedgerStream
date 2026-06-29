package com.ledgerstream.marketdata;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ledgerstream.events.MarketTickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketTickConsumerTest {

	@Mock
	private MarketTickIngestionService ingestionService;

	private MarketTickConsumer consumer;

	@BeforeEach
	void setUp() {
		consumer = new MarketTickConsumer(ingestionService);
	}

	@Test
	void receivePropagatesRejectedEventsForDeadLetterHandling() {
		MarketTickEvent event = marketTick();
		doThrow(new MarketTickRejectedException("bad tick")).when(ingestionService).ingest(event);

		assertThatThrownBy(() -> consumer.receive(event))
			.isInstanceOf(MarketTickRejectedException.class)
			.hasMessage("bad tick");

		verify(ingestionService).ingest(event);
	}

	@Test
	void receivePropagatesUnexpectedIngestionFailuresForRetryHandling() {
		MarketTickEvent event = marketTick();
		doThrow(new IllegalStateException("database down")).when(ingestionService).ingest(event);

		assertThatThrownBy(() -> consumer.receive(event))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("database down");

		verify(ingestionService).ingest(event);
	}

	private MarketTickEvent marketTick() {
		return new MarketTickEvent(
			UUID.randomUUID(),
			"AAPL",
			Instant.parse("2026-01-01T14:30:00Z"),
			new BigDecimal("187.120000"),
			new BigDecimal("187.180000"),
			new BigDecimal("187.150000"),
			1_000L,
			"fixture"
		);
	}
}
