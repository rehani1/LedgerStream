package com.ledgerstream.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import com.ledgerstream.config.properties.JwtProperties;
import com.ledgerstream.domain.model.RefreshToken;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

	private static final int TOKEN_BYTES = 32;

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtProperties jwtProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtProperties = jwtProperties;
	}

	public IssuedRefreshToken issue(User user) {
		String value = generateTokenValue();
		Instant expiresAt = Instant.now().plus(jwtProperties.refreshTokenTtl());

		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setUser(user);
		refreshToken.setTokenHash(hash(value));
		refreshToken.setExpiresAt(expiresAt);
		refreshTokenRepository.save(refreshToken);

		return new IssuedRefreshToken(value, expiresAt);
	}

	public Optional<RefreshToken> findByRawToken(String token) {
		return refreshTokenRepository.findByTokenHash(hash(token));
	}

	public void revoke(RefreshToken refreshToken, Instant revokedAt) {
		if (refreshToken.getRevokedAt() == null) {
			refreshToken.setRevokedAt(revokedAt);
			refreshTokenRepository.save(refreshToken);
		}
	}

	public void revokeActiveTokens(User user, Instant revokedAt) {
		refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())
			.forEach(refreshToken -> revoke(refreshToken, revokedAt));
	}

	public boolean isExpired(RefreshToken refreshToken, Instant now) {
		return !refreshToken.getExpiresAt().isAfter(now);
	}

	String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 digest is not available", ex);
		}
	}

	private String generateTokenValue() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public record IssuedRefreshToken(String value, Instant expiresAt) {
	}
}
