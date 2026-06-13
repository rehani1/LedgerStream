package com.ledgerstream.marketdata;

import com.ledgerstream.events.EventTopics;
import com.ledgerstream.events.MarketTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MarketTickConsumer {

	private static final Logger log = LoggerFactory.getLogger(MarketTickConsumer.class);

	private final MarketTickIngestionService ingestionService;

	public MarketTickConsumer(MarketTickIngestionService ingestionService) {
		this.ingestionService = ingestionService;
	}

	@KafkaListener(
		topics = EventTopics.MARKET_TICK,
		groupId = "${ledgerstream.kafka.consumer-group-id}",
		autoStartup = "${ledgerstream.kafka.market-tick-consumer-enabled:true}",
		containerFactory = "marketTickKafkaListenerContainerFactory"
	)
	public void receive(MarketTickEvent event) {
		try {
			ingestionService.ingest(event);
		} catch (MarketTickRejectedException ex) {
			log.warn("Rejected market tick event: {}", ex.getMessage());
		}
	}
}
