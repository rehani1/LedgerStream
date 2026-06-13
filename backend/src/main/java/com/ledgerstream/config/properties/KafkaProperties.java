package com.ledgerstream.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ledgerstream.kafka")
public record KafkaProperties(
	@NotBlank String bootstrapServers,
	@NotBlank String consumerGroupId,
	boolean connectivityConsumerEnabled,
	boolean marketTickConsumerEnabled
) {
}
