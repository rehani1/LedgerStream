package com.ledgerstream.auth;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.auth.dto.AuthResponse;
import com.ledgerstream.auth.dto.CurrentUserResponse;
import com.ledgerstream.auth.dto.LoginRequest;
import com.ledgerstream.auth.dto.RegisterRequest;
import com.ledgerstream.domain.model.Portfolio;
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
	private final AuditService auditService;

	public AuthService(
		UserRepository userRepository,
		PortfolioRepository portfolioRepository,
		PasswordEncoder passwordEncoder,
		JwtService jwtService,
		AuditService auditService
	) {
		this.userRepository = userRepository;
		this.portfolioRepository = portfolioRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
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
		return new AuthResponse(
			accessToken.value(),
			"Bearer",
			accessToken.expiresAt(),
			new CurrentUserResponse(user.getId(), user.getEmail(), user.getRole())
		);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
