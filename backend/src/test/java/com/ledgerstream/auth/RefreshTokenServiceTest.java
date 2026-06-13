package com.ledgerstream.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.config.properties.JwtProperties;
import com.ledgerstream.domain.model.RefreshToken;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private RefreshTokenService refreshTokenService;

	@BeforeEach
	void setUp() {
		JwtProperties jwtProperties = new JwtProperties(
			"ledgerstream-test",
			"test-secret",
			Duration.ofMinutes(15),
			Duration.ofDays(7)
		);
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtProperties);
	}

	@Test
	void issueStoresHashInsteadOfRawRefreshToken() {
		User user = testUser();
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
		Instant before = Instant.now();

		RefreshTokenService.IssuedRefreshToken issuedToken = refreshTokenService.issue(user);
		Instant after = Instant.now();

		ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(tokenCaptor.capture());
		RefreshToken savedToken = tokenCaptor.getValue();
		assertThat(issuedToken.value()).isNotBlank();
		assertThat(savedToken.getTokenHash()).isNotEqualTo(issuedToken.value());
		assertThat(savedToken.getTokenHash()).isEqualTo(refreshTokenService.hash(issuedToken.value()));
		assertThat(savedToken.getUser()).isEqualTo(user);
		assertThat(savedToken.getExpiresAt())
			.isBetween(before.plus(Duration.ofDays(7)), after.plus(Duration.ofDays(7)));
		assertThat(issuedToken.expiresAt()).isEqualTo(savedToken.getExpiresAt());
	}

	@Test
	void findByRawTokenUsesStoredHash() {
		RefreshToken refreshToken = new RefreshToken();
		when(refreshTokenRepository.findByTokenHash(refreshTokenService.hash("raw-refresh-token")))
			.thenReturn(Optional.of(refreshToken));

		assertThat(refreshTokenService.findByRawToken("raw-refresh-token")).contains(refreshToken);
	}

	private User testUser() {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail("demo@example.com");
		user.setRole(UserRole.USER);
		return user;
	}
}
