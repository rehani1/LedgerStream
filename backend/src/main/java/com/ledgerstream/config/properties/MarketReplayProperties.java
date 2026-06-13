package com.ledgerstream.config.properties;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ledgerstream.market-replay")
public record MarketReplayProperties(
	boolean enabled,
	@NotBlank String tickTopic,
	@DecimalMin("0.01") double replaySpeed
) {
}
