package com.ledgerstream.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MarketTickConnectivityConsumer {

	private static final Logger log = LoggerFactory.getLogger(MarketTickConnectivityConsumer.class);

	@KafkaListener(
		topics = EventTopics.MARKET_TICK,
		groupId = "${ledgerstream.kafka.consumer-group-id}",
		autoStartup = "${ledgerstream.kafka.connectivity-consumer-enabled:false}",
		containerFactory = "marketTickKafkaListenerContainerFactory"
	)
	public void receive(MarketTickEvent event) {
		log.debug("Received market tick connectivity event for symbol {}", event.symbol());
	}
}
