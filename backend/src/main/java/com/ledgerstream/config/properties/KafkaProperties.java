package com.ledgerstream.config.properties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ledgerstream.kafka")
public record KafkaProperties(
	@NotBlank String bootstrapServers,
	@NotBlank String consumerGroupId,
	boolean connectivityConsumerEnabled,
	boolean marketTickConsumerEnabled,
	boolean orderCreatedConsumerEnabled,
	@Min(0) long retryMaxAttempts,
	@NotNull Duration retryBackoff,
	@NotBlank String deadLetterSuffix,
	@NotNull Duration producerMaxBlock,
	@NotNull Duration producerRequestTimeout,
	@NotNull Duration producerDeliveryTimeout,
	String securityProtocol,
	String saslMechanism,
	String saslUsername,
	String saslPassword,
	String saslJaasConfig
) {
	public Map<String, Object> clientProperties() {
		Map<String, Object> properties = new LinkedHashMap<>();
		if (hasText(securityProtocol)) {
			properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol.trim());
		}
		if (hasText(saslMechanism)) {
			properties.put(SaslConfigs.SASL_MECHANISM, saslMechanism.trim());
		}
		String jaasConfig = jaasConfig();
		if (hasText(jaasConfig)) {
			properties.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
		}
		return properties;
	}

	public Map<String, Object> producerProperties() {
		Map<String, Object> properties = new LinkedHashMap<>(clientProperties());
		properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, producerMaxBlock.toMillis());
		properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) producerRequestTimeout.toMillis());
		properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) producerDeliveryTimeout.toMillis());
		return properties;
	}

	private String jaasConfig() {
		if (hasText(saslJaasConfig)) {
			return saslJaasConfig.trim();
		}
		if (!hasText(saslMechanism) || !hasText(saslUsername) || !hasText(saslPassword)) {
			return null;
		}
		String loginModule = "PLAIN".equalsIgnoreCase(saslMechanism.trim())
			? "org.apache.kafka.common.security.plain.PlainLoginModule"
			: "org.apache.kafka.common.security.scram.ScramLoginModule";
		return loginModule
			+ " required username=\""
			+ escapeJaasValue(saslUsername.trim())
			+ "\" password=\""
			+ escapeJaasValue(saslPassword.trim())
			+ "\";";
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String escapeJaasValue(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
