package com.ledgerstream.orders;

import com.ledgerstream.events.EventTopics;
import com.ledgerstream.events.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderExecutionConsumer {

	private final OrderExecutionService orderExecutionService;

	public OrderExecutionConsumer(OrderExecutionService orderExecutionService) {
		this.orderExecutionService = orderExecutionService;
	}

	@KafkaListener(
		topics = EventTopics.ORDER_CREATED,
		groupId = "${ledgerstream.kafka.consumer-group-id}",
		autoStartup = "${ledgerstream.kafka.order-created-consumer-enabled:true}",
		containerFactory = "orderCreatedKafkaListenerContainerFactory"
	)
	public void receive(OrderCreatedEvent event) {
		orderExecutionService.execute(event);
	}
}
