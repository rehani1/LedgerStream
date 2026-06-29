package com.ledgerstream.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerStreamMetricsTest {

	private SimpleMeterRegistry meterRegistry;
	private LedgerStreamMetrics metrics;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		metrics = new LedgerStreamMetrics(meterRegistry);
	}

	@Test
	void recordsEventConsumerRetryWithTopicAndExceptionTags() {
		metrics.recordEventConsumerRetry("market.tick", new IllegalStateException("database down"));

		assertThat(meterRegistry.get(LedgerStreamMetrics.EVENT_CONSUMER_RETRIES)
			.tag("topic", "market.tick")
			.tag("exception", "IllegalStateException")
			.counter()
			.count()).isEqualTo(1.0);
	}

	@Test
	void recordsEventConsumerDeadLetterWithSourceAndTargetTopicTags() {
		metrics.recordEventConsumerDeadLetter(
			"order.created",
			"order.created.DLT",
			new RuntimeException("exhausted")
		);

		assertThat(meterRegistry.get(LedgerStreamMetrics.EVENT_CONSUMER_DEAD_LETTERS)
			.tag("topic", "order.created")
			.tag("dead_letter_topic", "order.created.DLT")
			.tag("exception", "RuntimeException")
			.counter()
			.count()).isEqualTo(1.0);
	}
}
