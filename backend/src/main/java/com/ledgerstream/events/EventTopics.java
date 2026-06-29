package com.ledgerstream.events;

public final class EventTopics {

	public static final String MARKET_TICK = "market.tick";
	public static final String ORDER_CREATED = "order.created";
	public static final String ORDER_FILLED = "order.filled";
	public static final String PORTFOLIO_UPDATED = "portfolio.updated";
	public static final String RISK_UPDATED = "risk.updated";
	public static final String AUDIT_EVENT = "audit.event";
	public static final String DEFAULT_DEAD_LETTER_SUFFIX = ".DLT";

	private EventTopics() {
	}

	public static String deadLetterTopic(String sourceTopic, String suffix) {
		String resolvedSuffix = suffix == null || suffix.isBlank() ? DEFAULT_DEAD_LETTER_SUFFIX : suffix.trim();
		return sourceTopic + resolvedSuffix;
	}
}
