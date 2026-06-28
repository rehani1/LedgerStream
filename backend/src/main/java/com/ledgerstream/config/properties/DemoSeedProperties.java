package com.ledgerstream.config.properties;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ledgerstream.demo-seed")
public record DemoSeedProperties(
	boolean enabled,
	@NotBlank @Email String email,
	@NotBlank String password,
	@NotNull @DecimalMin("0.00") BigDecimal initialCash,
	boolean adminEnabled,
	@NotBlank @Email String adminEmail,
	@NotBlank String adminPassword
) {
}
