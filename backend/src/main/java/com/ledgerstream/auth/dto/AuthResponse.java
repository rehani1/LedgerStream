package com.ledgerstream.auth.dto;

import java.time.Instant;

public record AuthResponse(
	String accessToken,
	String tokenType,
	Instant expiresAt,
	CurrentUserResponse user
) {
}
