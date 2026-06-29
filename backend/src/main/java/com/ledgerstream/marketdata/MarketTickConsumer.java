package com.ledgerstream.marketdata;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ledgerstream.events.EventTopics;
import com.ledgerstream.events.MarketTickEvent;
import com.ledgerstream.logging.MdcScope;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MarketTickConsumer {

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
		try (MdcScope ignored = marketTickContext(event)) {
			ingestionService.ingest(event);
		}
	}

	private MdcScope marketTickContext(MarketTickEvent event) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("eventType", "market.tick");
		if (event != null) {
			context.put("symbol", event.symbol());
		}
		return MdcScope.put(context);
	}
}
