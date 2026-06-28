package com.ledgerstream.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ledgerstream.config.properties.KafkaProperties;
import com.ledgerstream.events.MarketTickEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

class EventStreamingConfigurationTest {

	private final EventStreamingConfiguration configuration = new EventStreamingConfiguration();
	private final KafkaProperties kafkaProperties = new KafkaProperties(
		"localhost:19092",
		"ledgerstream-test",
		false,
		false,
		false
	);

	@Test
	void producerFactoryUsesJsonAndIdempotentAcks() {
		ProducerFactory<String, Object> producerFactory = configuration.eventProducerFactory(
			kafkaProperties,
			JsonMapper.builder().findAndAddModules().build()
		);

		Map<String, Object> config = ((DefaultKafkaProducerFactory<String, Object>) producerFactory)
			.getConfigurationProperties();
		assertThat(config).containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
		assertThat(config).containsEntry(ProducerConfig.ACKS_CONFIG, "all");
		assertThat(config).containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		assertThat(config).doesNotContainKey(JsonSerializer.ADD_TYPE_INFO_HEADERS);
		assertThatCode(() -> {
			Producer<String, Object> producer = producerFactory.createProducer();
			producer.close(Duration.ZERO);
		}).doesNotThrowAnyException();
	}

	@Test
	void marketTickConsumerFactoryUsesConfiguredGroup() {
		ConsumerFactory<String, MarketTickEvent> consumerFactory = configuration.marketTickConsumerFactory(
			kafkaProperties,
			JsonMapper.builder().findAndAddModules().build()
		);

		assertThat(consumerFactory.getConfigurationProperties())
			.containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092")
			.containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "ledgerstream-test")
			.containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
			.doesNotContainKey(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)
			.doesNotContainKey(JsonDeserializer.TRUSTED_PACKAGES);
	}
}
