package com.ledgerstream.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ledgerstream.config.properties.KafkaProperties;
import com.ledgerstream.events.EventTopics;
import com.ledgerstream.events.MarketTickEvent;
import com.ledgerstream.metrics.LedgerStreamMetrics;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

class EventStreamingConfigurationTest {

	private EventStreamingConfiguration configuration;
	private KafkaProperties kafkaProperties;

	@BeforeEach
	void setUp() {
		configuration = new EventStreamingConfiguration();
		kafkaProperties = kafkaProperties();
	}

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
		assertThat(config).containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000L);
		assertThat(config).containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
		assertThat(config).containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15000);
		assertThat(config).doesNotContainKey(JsonSerializer.ADD_TYPE_INFO_HEADERS);
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
			3,
			Duration.ofSeconds(2),
			EventTopics.DEFAULT_DEAD_LETTER_SUFFIX,
			Duration.ofSeconds(5),
			Duration.ofSeconds(10),
			Duration.ofSeconds(15),
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

	@Test
	void listenerFactoriesUseSharedErrorHandler() {
		ConsumerFactory<String, MarketTickEvent> consumerFactory = configuration.marketTickConsumerFactory(
			kafkaProperties,
			JsonMapper.builder().findAndAddModules().build()
		);
		DefaultErrorHandler errorHandler = configuration.eventKafkaErrorHandler(
			Mockito.mock(KafkaTemplate.class),
			kafkaProperties,
			Mockito.mock(LedgerStreamMetrics.class)
		);

		ConcurrentKafkaListenerContainerFactory<String, MarketTickEvent> factory =
			configuration.marketTickKafkaListenerContainerFactory(consumerFactory, errorHandler);

		assertThat(factory.createContainer(EventTopics.MARKET_TICK).getCommonErrorHandler()).isSameAs(errorHandler);
	}

	@Test
	void deadLetterTopicUsesConfiguredSuffix() {
		assertThat(EventTopics.deadLetterTopic(EventTopics.MARKET_TICK, ".DLT")).isEqualTo("market.tick.DLT");
		assertThat(EventTopics.deadLetterTopic(EventTopics.ORDER_CREATED, ""))
			.isEqualTo("order.created" + EventTopics.DEFAULT_DEAD_LETTER_SUFFIX);
	}

	private KafkaProperties kafkaProperties() {
		return new KafkaProperties(
			"localhost:19092",
			"ledgerstream-test",
			false,
			false,
			false,
			3,
			Duration.ofSeconds(2),
			EventTopics.DEFAULT_DEAD_LETTER_SUFFIX,
			Duration.ofSeconds(5),
			Duration.ofSeconds(10),
			Duration.ofSeconds(15),
			null,
			null,
			null,
			null,
			null
		);
	}
}
