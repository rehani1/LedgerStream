package com.ledgerstream.auth;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.auth.dto.AuthResponse;
import com.ledgerstream.auth.dto.CurrentUserResponse;
import com.ledgerstream.auth.dto.LoginRequest;
import com.ledgerstream.auth.dto.RegisterRequest;
import com.ledgerstream.auth.dto.RefreshTokenRequest;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.RefreshToken;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private static final String INVALID_LOGIN_MESSAGE = "Invalid email or password";

	private final UserRepository userRepository;
	private final PortfolioRepository portfolioRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final AuditService auditService;

	public AuthService(
		UserRepository userRepository,
		PortfolioRepository portfolioRepository,
		PasswordEncoder passwordEncoder,
		JwtService jwtService,
		RefreshTokenService refreshTokenService,
		AuditService auditService
	) {
		this.userRepository = userRepository;
		this.portfolioRepository = portfolioRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
		this.auditService = auditService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request, String requestId) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
		}

		User user = new User();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setRole(UserRole.USER);
		user = userRepository.save(user);

		Portfolio portfolio = new Portfolio();
		portfolio.setUser(user);
		portfolio.setCashBalance(BigDecimal.ZERO);
		portfolio.setBaseCurrency("USD");
		portfolioRepository.save(portfolio);

		auditService.record(user, "USER_REGISTERED", requestId, Map.of());
		return tokenResponse(user);
	}

	@Transactional(noRollbackFor = ResponseStatusException.class)
	public AuthResponse login(LoginRequest request, String requestId) {
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			auditService.record(user, "LOGIN_FAILED", requestId, Map.of("reason", "invalid_credentials"));
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN_MESSAGE);
		}

		auditService.record(user, "LOGIN_SUCCEEDED", requestId, Map.of());
		return tokenResponse(user);
	}

	@Transactional(noRollbackFor = ResponseStatusException.class)
	public AuthResponse refresh(RefreshTokenRequest request, String requestId) {
		Instant now = Instant.now();
		RefreshToken refreshToken = refreshTokenService.findByRawToken(request.refreshToken()).orElse(null);
		if (refreshToken == null) {
			auditService.record(null, "REFRESH_TOKEN_FAILED", requestId, Map.of("reason", "invalid_token"));
			throw invalidRefreshToken();
		}

		User user = refreshToken.getUser();
		if (refreshToken.getRevokedAt() != null) {
			refreshTokenService.revokeActiveTokens(user, now);
			auditService.record(user, "REFRESH_TOKEN_REUSE_DETECTED", requestId, Map.of());
			throw invalidRefreshToken();
		}

		if (refreshTokenService.isExpired(refreshToken, now)) {
			refreshTokenService.revoke(refreshToken, now);
			auditService.record(user, "REFRESH_TOKEN_FAILED", requestId, Map.of("reason", "expired_token"));
			throw invalidRefreshToken();
		}

		refreshTokenService.revoke(refreshToken, now);
		RefreshTokenService.IssuedRefreshToken replacement = refreshTokenService.issue(user);
		auditService.record(user, "REFRESH_TOKEN_ROTATED", requestId, Map.of());
		return tokenResponse(user, replacement);
	}

	@Transactional
	public void logout(RefreshTokenRequest request, String requestId) {
		Instant now = Instant.now();
		refreshTokenService.findByRawToken(request.refreshToken()).ifPresent(refreshToken -> {
			refreshTokenService.revoke(refreshToken, now);
			auditService.record(refreshToken.getUser(), "LOGOUT", requestId, Map.of());
		});
	}

	@Transactional(readOnly = true)
	public CurrentUserResponse currentUser(AuthenticatedUser authenticatedUser) {
		if (authenticatedUser == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
		}
		return new CurrentUserResponse(authenticatedUser.id(), authenticatedUser.email(), authenticatedUser.role());
	}

	public AuthenticatedUser principalFor(User user) {
		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
	}

	private AuthResponse tokenResponse(User user) {
		JwtService.AccessToken accessToken = jwtService.issueAccessToken(user);
		RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);
		return tokenResponse(user, refreshToken, accessToken);
	}

	private AuthResponse tokenResponse(User user, RefreshTokenService.IssuedRefreshToken refreshToken) {
		JwtService.AccessToken accessToken = jwtService.issueAccessToken(user);
		return tokenResponse(user, refreshToken, accessToken);
	}

	private AuthResponse tokenResponse(
		User user,
		RefreshTokenService.IssuedRefreshToken refreshToken,
		JwtService.AccessToken accessToken
	) {
		return new AuthResponse(
			accessToken.value(),
			"Bearer",
			accessToken.expiresAt(),
			refreshToken.value(),
			refreshToken.expiresAt(),
			new CurrentUserResponse(user.getId(), user.getEmail(), user.getRole())
		);
	}

	private ResponseStatusException invalidRefreshToken() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
