package com.ledgerstream.config;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerstream.config.properties.KafkaProperties;
import com.ledgerstream.events.EventTopics;
import com.ledgerstream.events.MarketTickEvent;
import com.ledgerstream.events.OrderCreatedEvent;
import com.ledgerstream.marketdata.MarketTickRejectedException;
import com.ledgerstream.metrics.LedgerStreamMetrics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@EnableKafka
@Configuration
public class EventStreamingConfiguration {

	@Bean
	ProducerFactory<String, Object> eventProducerFactory(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		config.put(ProducerConfig.ACKS_CONFIG, "all");
		config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		config.putAll(kafkaProperties.producerProperties());

		JsonSerializer<Object> valueSerializer = new JsonSerializer<>(objectMapper);
		valueSerializer.setAddTypeInfo(false);
		DefaultKafkaProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(config);
		producerFactory.setValueSerializer(valueSerializer);
		return producerFactory;
	}

	@Bean
	KafkaTemplate<String, Object> eventKafkaTemplate(ProducerFactory<String, Object> eventProducerFactory) {
		return new KafkaTemplate<>(eventProducerFactory);
	}

	@Bean
	ConsumerFactory<String, MarketTickEvent> marketTickConsumerFactory(
		KafkaProperties kafkaProperties,
		ObjectMapper objectMapper
	) {
		Map<String, Object> config = new HashMap<>();
		config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
		config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.consumerGroupId());
		config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
		config.putAll(kafkaProperties.clientProperties());

		JsonDeserializer<MarketTickEvent> valueDeserializer = new JsonDeserializer<>(
			MarketTickEvent.class,
			objectMapper,
			false
		);
		valueDeserializer.addTrustedPackages("com.ledgerstream.events");
		return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueDeserializer);
	}

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, MarketTickEvent> marketTickKafkaListenerContainerFactory(
		ConsumerFactory<String, MarketTickEvent> marketTickConsumerFactory,
		DefaultErrorHandler eventKafkaErrorHandler
	) {
		ConcurrentKafkaListenerContainerFactory<String, MarketTickEvent> factory =
			new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(marketTickConsumerFactory);
		factory.setCommonErrorHandler(eventKafkaErrorHandler);
		return factory;
	}

	@Bean
	ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory(
		KafkaProperties kafkaProperties,
		ObjectMapper objectMapper
	) {
		Map<String, Object> config = new HashMap<>();
		config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
		config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.consumerGroupId());
		config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
		config.putAll(kafkaProperties.clientProperties());

		JsonDeserializer<OrderCreatedEvent> valueDeserializer = new JsonDeserializer<>(
			OrderCreatedEvent.class,
			objectMapper,
			false
		);
		valueDeserializer.addTrustedPackages("com.ledgerstream.events");
		return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueDeserializer);
	}

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> orderCreatedKafkaListenerContainerFactory(
		ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory,
		DefaultErrorHandler eventKafkaErrorHandler
	) {
		ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
			new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(orderCreatedConsumerFactory);
		factory.setCommonErrorHandler(eventKafkaErrorHandler);
		return factory;
	}

	@Bean
	DefaultErrorHandler eventKafkaErrorHandler(
		KafkaTemplate<String, Object> eventKafkaTemplate,
		KafkaProperties kafkaProperties,
		LedgerStreamMetrics metrics
	) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
			eventKafkaTemplate,
			(record, exception) -> {
				String deadLetterTopic = EventTopics.deadLetterTopic(record.topic(), kafkaProperties.deadLetterSuffix());
				metrics.recordEventConsumerDeadLetter(record.topic(), deadLetterTopic, exception);
				return new TopicPartition(deadLetterTopic, record.partition());
			}
		);
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(
			recoverer,
			new FixedBackOff(kafkaProperties.retryBackoff().toMillis(), kafkaProperties.retryMaxAttempts())
		);
		errorHandler.addNotRetryableExceptions(MarketTickRejectedException.class);
		errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> {
			if (deliveryAttempt > 1) {
				metrics.recordEventConsumerRetry(record.topic(), exception);
			}
		});
		return errorHandler;
	}
}
