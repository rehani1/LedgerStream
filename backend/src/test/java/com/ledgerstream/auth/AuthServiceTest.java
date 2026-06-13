package com.ledgerstream.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.auth.dto.AuthResponse;
import com.ledgerstream.auth.dto.LoginRequest;
import com.ledgerstream.auth.dto.RegisterRequest;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private JwtService jwtService;

	@Mock
	private AuditService auditService;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userRepository, portfolioRepository, passwordEncoder, jwtService, auditService);
	}

	@Test
	void registerCreatesUserPortfolioAuditEventAndAccessToken() {
		Instant expiresAt = Instant.parse("2026-01-01T00:15:00Z");
		when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});
		when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtService.issueAccessToken(any(User.class))).thenReturn(new JwtService.AccessToken("access-token", expiresAt));

		AuthResponse response = authService.register(
			new RegisterRequest("New@Example.com", "Password123"),
			"request-1"
		);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		User user = userCaptor.getValue();
		assertThat(user.getEmail()).isEqualTo("new@example.com");
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		assertThat(passwordEncoder.matches("Password123", user.getPasswordHash())).isTrue();

		ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
		verify(portfolioRepository).save(portfolioCaptor.capture());
		assertThat(portfolioCaptor.getValue().getCashBalance()).isEqualByComparingTo("0.00");

		verify(auditService).record(eq(user), eq("USER_REGISTERED"), eq("request-1"), eq(Map.of()));
		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresAt()).isEqualTo(expiresAt);
		assertThat(response.user().email()).isEqualTo("new@example.com");
	}

	@Test
	void registerRejectsDuplicateEmailCleanly() {
		when(userRepository.existsByEmail("dupe@example.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(new RegisterRequest("dupe@example.com", "Password123"), "request-2"))
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(ex.getReason()).isEqualTo("Email is already registered");
			});

		verify(userRepository, never()).save(any(User.class));
		verify(portfolioRepository, never()).save(any(Portfolio.class));
	}

	@Test
	void loginReturnsGenericFailureAndAuditsInvalidCredentials() {
		when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "bad-password"), "request-3"))
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
				assertThat(ex.getReason()).isEqualTo("Invalid email or password");
			});

		verify(auditService).record(null, "LOGIN_FAILED", "request-3", Map.of("reason", "invalid_credentials"));
	}

	@Test
	void loginIssuesAccessTokenForValidCredentials() {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail("demo@example.com");
		user.setRole(UserRole.USER);
		user.setPasswordHash(passwordEncoder.encode("Password123"));
		Instant expiresAt = Instant.parse("2026-01-01T00:15:00Z");
		when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));
		when(jwtService.issueAccessToken(user)).thenReturn(new JwtService.AccessToken("access-token", expiresAt));

		AuthResponse response = authService.login(new LoginRequest("demo@example.com", "Password123"), "request-4");

		verify(auditService).record(user, "LOGIN_SUCCEEDED", "request-4", Map.of());
		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.user().id()).isEqualTo(user.getId());
		assertThat(response.user().role()).isEqualTo(UserRole.USER);
	}
}
