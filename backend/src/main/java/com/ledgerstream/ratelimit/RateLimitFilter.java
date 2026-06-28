package com.ledgerstream.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.config.properties.RateLimitProperties;
import com.ledgerstream.config.properties.RateLimitProperties.Policy;
import com.ledgerstream.ratelimit.FixedWindowRateLimiter.RateLimitResult;
import com.ledgerstream.web.ApiErrorResponse;
import com.ledgerstream.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimitProperties properties;
	private final FixedWindowRateLimiter rateLimiter;
	private final ObjectMapper objectMapper;

	public RateLimitFilter(
		RateLimitProperties properties,
		FixedWindowRateLimiter rateLimiter,
		ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.rateLimiter = rateLimiter;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		EndpointPolicy endpointPolicy = endpointPolicy(request);
		if (endpointPolicy == null || !properties.enabled() || !endpointPolicy.policy().enabled()) {
			filterChain.doFilter(request, response);
			return;
		}

		RateLimitResult result = rateLimiter.consume(
			rateLimitKey(endpointPolicy.name(), request),
			endpointPolicy.policy().maxRequests(),
			endpointPolicy.policy().window()
		);
		if (result.allowed()) {
			response.setHeader("X-RateLimit-Limit", String.valueOf(endpointPolicy.policy().maxRequests()));
			response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
			response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetAt().getEpochSecond()));
			filterChain.doFilter(request, response);
			return;
		}

		writeTooManyRequests(request, response, endpointPolicy, result);
	}

	private EndpointPolicy endpointPolicy(HttpServletRequest request) {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return null;
		}
		String path = pathWithoutContext(request);
		if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/auth/login".equals(path)) {
			return new EndpointPolicy("login", properties.login());
		}
		if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/auth/register".equals(path)) {
			return new EndpointPolicy("registration", properties.registration());
		}
		if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/orders".equals(path)) {
			return new EndpointPolicy("order_creation", properties.orderCreation());
		}
		if ("GET".equalsIgnoreCase(request.getMethod()) && "/api/stream/quotes".equals(path)) {
			return new EndpointPolicy("quote_stream", properties.quoteStream());
		}
		return null;
	}

	private String pathWithoutContext(HttpServletRequest request) {
		String contextPath = request.getContextPath();
		String uri = request.getRequestURI();
		if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
			return uri.substring(contextPath.length());
		}
		return uri;
	}

	private String rateLimitKey(String policyName, HttpServletRequest request) {
		return policyName + ":" + principalOrClientKey(request);
	}

	private String principalOrClientKey(HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
			Object principal = authentication.getPrincipal();
			if (principal instanceof AuthenticatedUser authenticatedUser) {
				return "user:" + authenticatedUser.id();
			}
			String name = authentication.getName() == null ? "unknown" : authentication.getName();
			return "principal:" + hash(name);
		}
		return "client:" + hash(clientAddress(request));
	}

	private String clientAddress(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		String remoteAddress = request.getRemoteAddr();
		return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
	}

	private String hash(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 12);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	private void writeTooManyRequests(
		HttpServletRequest request,
		HttpServletResponse response,
		EndpointPolicy endpointPolicy,
		RateLimitResult result
	) throws IOException {
		HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
		long retryAfterSeconds = Math.max(1, result.retryAfter().toSeconds());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
		response.setHeader("X-RateLimit-Limit", String.valueOf(endpointPolicy.policy().maxRequests()));
		response.setHeader("X-RateLimit-Remaining", "0");
		response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetAt().getEpochSecond()));

		ApiErrorResponse body = new ApiErrorResponse(
			Instant.now(),
			status.value(),
			status.getReasonPhrase(),
			"Rate limit exceeded for " + endpointPolicy.name().replace('_', ' ') + ". Retry later.",
			request.getRequestURI(),
			requestId(request)
		);
		objectMapper.writeValue(response.getOutputStream(), body);
	}

	private String requestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return requestId == null ? null : requestId.toString();
	}

	private record EndpointPolicy(String name, Policy policy) {
	}
}
