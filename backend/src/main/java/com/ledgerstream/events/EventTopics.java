package com.ledgerstream.events;

public final class EventTopics {

	public static final String MARKET_TICK = "market.tick";
	public static final String ORDER_CREATED = "order.created";
	public static final String ORDER_FILLED = "order.filled";
	public static final String PORTFOLIO_UPDATED = "portfolio.updated";
	public static final String RISK_UPDATED = "risk.updated";
	public static final String AUDIT_EVENT = "audit.event";

	private EventTopics() {
	}
}
