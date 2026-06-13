package com.ledgerstream.events;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaEventPublisher implements EventPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	@Override
	public CompletableFuture<Void> publish(String topic, String key, Object event) {
		return kafkaTemplate.send(topic, key, event).thenApply(result -> null);
	}
}
