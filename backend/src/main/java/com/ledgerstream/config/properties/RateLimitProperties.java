package com.ledgerstream.config.properties;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ledgerstream.rate-limit")
public record RateLimitProperties(
	boolean enabled,
	@Valid @NotNull Policy login,
	@Valid @NotNull Policy registration,
	@Valid @NotNull Policy orderCreation,
	@Valid @NotNull Policy quoteStream
) {

	public record Policy(
		boolean enabled,
		@Min(1) int maxRequests,
		@NotNull Duration window
	) {
	}
}
