package com.ledgerstream.auth.dto;

import java.time.Instant;

public record AuthResponse(
	String accessToken,
	String tokenType,
	Instant expiresAt,
	String refreshToken,
	Instant refreshTokenExpiresAt,
	CurrentUserResponse user
) {
}
