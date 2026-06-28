package com.ledgerstream.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ledgerstream.config.properties.KafkaProperties;
import com.ledgerstream.events.MarketTickEvent;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
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
		false,
		null,
		null,
		null,
		null,
		null
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

	@Test
	void kafkaSecurityPropertiesAreAppliedToProducerAndConsumerFactories() {
		KafkaProperties secureProperties = new KafkaProperties(
			"broker.example.com:9092",
			"ledgerstream-prod",
			false,
			true,
			true,
			"SASL_SSL",
			"SCRAM-SHA-256",
			"service-user",
			"service-password",
			null
		);

		Map<String, Object> producerConfig =
			((DefaultKafkaProducerFactory<String, Object>) configuration.eventProducerFactory(
				secureProperties,
				JsonMapper.builder().findAndAddModules().build()
			)).getConfigurationProperties();
		ConsumerFactory<String, MarketTickEvent> consumerFactory = configuration.marketTickConsumerFactory(
			secureProperties,
			JsonMapper.builder().findAndAddModules().build()
		);

		assertThat(producerConfig)
			.containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
			.containsEntry(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-256");
		assertThat(producerConfig.get(SaslConfigs.SASL_JAAS_CONFIG).toString())
			.contains("ScramLoginModule")
			.contains("service-user")
			.contains("service-password");
		assertThat(consumerFactory.getConfigurationProperties())
			.containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
			.containsEntry(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-256");
	}
}
