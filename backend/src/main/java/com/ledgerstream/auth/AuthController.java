package com.ledgerstream.auth;

import com.ledgerstream.auth.dto.AuthResponse;
import com.ledgerstream.auth.dto.CurrentUserResponse;
import com.ledgerstream.auth.dto.LoginRequest;
import com.ledgerstream.auth.dto.RegisterRequest;
import com.ledgerstream.auth.dto.RefreshTokenRequest;
import com.ledgerstream.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/auth/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
		return authService.register(request, requestId(servletRequest));
	}

	@PostMapping("/auth/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return authService.login(request, requestId(servletRequest));
	}

	@PostMapping("/auth/refresh")
	public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
		return authService.refresh(request, requestId(servletRequest));
	}

	@PostMapping("/auth/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
		authService.logout(request, requestId(servletRequest));
	}

	@GetMapping("/me")
	public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		return authService.currentUser(authenticatedUser);
	}

	private String requestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return requestId == null ? null : requestId.toString();
	}
}
