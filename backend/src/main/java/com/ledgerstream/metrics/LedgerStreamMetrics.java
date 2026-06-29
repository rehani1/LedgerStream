package com.ledgerstream.metrics;

import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class LedgerStreamMetrics {

	public static final String ORDERS_CREATED = "ledgerstream_orders_created_total";
	public static final String ORDERS_FILLED = "ledgerstream_orders_filled_total";
	public static final String ORDERS_REJECTED = "ledgerstream_orders_rejected_total";
	public static final String QUOTE_CACHE_HITS = "ledgerstream_quote_cache_hits_total";
	public static final String QUOTE_CACHE_MISSES = "ledgerstream_quote_cache_misses_total";
	public static final String EVENT_CONSUMER_RETRIES = "ledgerstream_event_consumer_retries_total";
	public static final String EVENT_CONSUMER_DEAD_LETTERS = "ledgerstream_event_consumer_dead_letters_total";
	public static final String PORTFOLIO_CALCULATION_LATENCY = "ledgerstream_portfolio_calculation_latency";

	private final MeterRegistry meterRegistry;
	private final Counter ordersCreated;
	private final Counter ordersFilled;
	private final Counter ordersRejected;
	private final Counter quoteCacheHits;
	private final Counter quoteCacheMisses;
	private final Timer portfolioCalculationLatency;

	public LedgerStreamMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		this.ordersCreated = Counter.builder(ORDERS_CREATED)
			.description("Paper orders newly accepted by the backend")
			.register(meterRegistry);
		this.ordersFilled = Counter.builder(ORDERS_FILLED)
			.description("Paper orders filled by the execution engine")
			.register(meterRegistry);
		this.ordersRejected = Counter.builder(ORDERS_REJECTED)
			.description("Paper orders rejected by the execution engine")
			.register(meterRegistry);
		this.quoteCacheHits = Counter.builder(QUOTE_CACHE_HITS)
			.description("Latest quote lookups served from Redis")
			.register(meterRegistry);
		this.quoteCacheMisses = Counter.builder(QUOTE_CACHE_MISSES)
			.description("Latest quote lookups that fell back to PostgreSQL")
			.register(meterRegistry);
		this.portfolioCalculationLatency = Timer.builder(PORTFOLIO_CALCULATION_LATENCY)
			.description("Latency of portfolio valuation calculations")
			.register(meterRegistry);
	}

	public void recordOrderCreated() {
		ordersCreated.increment();
	}

	public void recordOrderFilled() {
		ordersFilled.increment();
	}

	public void recordOrderRejected() {
		ordersRejected.increment();
	}

	public void recordQuoteCacheHit() {
		quoteCacheHits.increment();
	}

	public void recordQuoteCacheMiss() {
		quoteCacheMisses.increment();
	}

	public void recordEventConsumerRetry(String topic, Throwable exception) {
		Counter.builder(EVENT_CONSUMER_RETRIES)
			.description("Kafka listener delivery attempts retried before dead-letter recovery")
			.tag("topic", safeTag(topic))
			.tag("exception", exceptionTag(exception))
			.register(meterRegistry)
			.increment();
	}

	public void recordEventConsumerDeadLetter(String sourceTopic, String deadLetterTopic, Throwable exception) {
		Counter.builder(EVENT_CONSUMER_DEAD_LETTERS)
			.description("Kafka listener records published to dead-letter topics")
			.tag("topic", safeTag(sourceTopic))
			.tag("dead_letter_topic", safeTag(deadLetterTopic))
			.tag("exception", exceptionTag(exception))
			.register(meterRegistry)
			.increment();
	}

	public <T> T recordPortfolioCalculation(Supplier<T> supplier) {
		return portfolioCalculationLatency.record(supplier);
	}

	private String safeTag(String value) {
		return value == null || value.isBlank() ? "unknown" : value;
	}

	private String exceptionTag(Throwable exception) {
		if (exception == null) {
			return "unknown";
		}
		Throwable cause = exception.getCause() == null ? exception : exception.getCause();
		return cause.getClass().getSimpleName();
	}
}
