package com.ledgerstream.events;

import java.util.concurrent.CompletableFuture;

public interface EventPublisher {

	CompletableFuture<Void> publish(String topic, String key, Object event);

	default CompletableFuture<Void> publishMarketTick(MarketTickEvent event) {
		return publish(EventTopics.MARKET_TICK, event.symbol(), event);
	}

	default CompletableFuture<Void> publishOrderCreated(OrderCreatedEvent event) {
		return publish(EventTopics.ORDER_CREATED, event.orderId().toString(), event);
	}

	default CompletableFuture<Void> publishOrderFilled(OrderFilledEvent event) {
		return publish(EventTopics.ORDER_FILLED, event.orderId().toString(), event);
	}

	default CompletableFuture<Void> publishPortfolioUpdated(PortfolioUpdatedEvent event) {
		return publish(EventTopics.PORTFOLIO_UPDATED, event.userId().toString(), event);
	}

	default CompletableFuture<Void> publishRiskUpdated(RiskUpdatedEvent event) {
		return publish(EventTopics.RISK_UPDATED, event.userId().toString(), event);
	}

	default CompletableFuture<Void> publishAuditEvent(AuditEventPayload event) {
		String key = event.userId() == null ? event.action() : event.userId().toString();
		return publish(EventTopics.AUDIT_EVENT, key, event);
	}
}
