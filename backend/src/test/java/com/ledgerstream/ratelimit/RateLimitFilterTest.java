package com.ledgerstream.ratelimit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ledgerstream.auth.AuthService;
import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.auth.dto.AuthResponse;
import com.ledgerstream.auth.dto.CurrentUserResponse;
import com.ledgerstream.auth.dto.LoginRequest;
import com.ledgerstream.auth.dto.RegisterRequest;
import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.orders.CreateOrderResult;
import com.ledgerstream.orders.OrderService;
import com.ledgerstream.orders.dto.CreateOrderRequest;
import com.ledgerstream.orders.dto.OrderResponse;
import com.ledgerstream.quotes.QuoteStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ActiveProfiles("test")
@SpringBootTest(properties = {
	"ledgerstream.rate-limit.login.max-requests=1",
	"ledgerstream.rate-limit.registration.max-requests=1",
	"ledgerstream.rate-limit.order-creation.max-requests=1",
	"ledgerstream.rate-limit.quote-stream.max-requests=1",
	"ledgerstream.rate-limit.login.window=1m",
	"ledgerstream.rate-limit.registration.window=1m",
	"ledgerstream.rate-limit.order-creation.window=1m",
	"ledgerstream.rate-limit.quote-stream.window=1m"
})
@AutoConfigureMockMvc
class RateLimitFilterTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private OrderService orderService;

	@MockitoBean
	private QuoteStreamService quoteStreamService;

	@Test
	void loginReturnsTooManyRequestsAfterConfiguredLimit() throws Exception {
		when(authService.login(any(LoginRequest.class), anyString())).thenReturn(authResponse());

		mockMvc.perform(post("/api/auth/login")
				.header("X-Forwarded-For", "198.51.100.10")
				.contentType("application/json")
				.content(loginJson()))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/login")
				.header("X-Forwarded-For", "198.51.100.10")
				.header("X-Request-ID", "rate-login-1")
				.contentType("application/json")
				.content(loginJson()))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("X-RateLimit-Remaining", "0"))
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.message").value("Rate limit exceeded for login. Retry later."))
			.andExpect(jsonPath("$.path").value("/api/auth/login"))
			.andExpect(jsonPath("$.requestId").value("rate-login-1"));
	}

	@Test
	void registrationReturnsTooManyRequestsAfterConfiguredLimit() throws Exception {
		when(authService.register(any(RegisterRequest.class), anyString())).thenReturn(authResponse());

		mockMvc.perform(post("/api/auth/register")
				.header("X-Forwarded-For", "198.51.100.11")
				.contentType("application/json")
				.content(registerJson()))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register")
				.header("X-Forwarded-For", "198.51.100.11")
				.contentType("application/json")
				.content(registerJson()))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.message").value("Rate limit exceeded for registration. Retry later."));
	}

	@Test
	void orderCreationReturnsTooManyRequestsForSameAuthenticatedUser() throws Exception {
		OrderResponse order = orderResponse();
		when(orderService.createOrder(any(AuthenticatedUser.class), eq("rate-order-1"), any(CreateOrderRequest.class), anyString()))
			.thenReturn(new CreateOrderResult(order, true));

		mockMvc.perform(post("/api/orders")
				.with(authentication(authenticatedUser(USER_ID)))
				.header("Idempotency-Key", "rate-order-1")
				.contentType("application/json")
				.content(orderJson()))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/orders")
				.with(authentication(authenticatedUser(USER_ID)))
				.header("Idempotency-Key", "rate-order-2")
				.contentType("application/json")
				.content(orderJson()))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.message").value("Rate limit exceeded for order creation. Retry later."));
	}

	@Test
	void quoteStreamReturnsTooManyRequestsForSameAuthenticatedUser() throws Exception {
		SseEmitter emitter = new SseEmitter(1_000L);
		when(quoteStreamService.openStream("AAPL")).thenReturn(emitter);

		mockMvc.perform(get("/api/stream/quotes")
				.param("symbols", "AAPL")
				.with(authentication(authenticatedUser(USER_ID))))
			.andExpect(status().isOk())
			.andExpect(request().asyncStarted());
		emitter.complete();

		mockMvc.perform(get("/api/stream/quotes")
				.param("symbols", "AAPL")
				.with(authentication(authenticatedUser(USER_ID))))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.message").value("Rate limit exceeded for quote stream. Retry later."));

		verify(quoteStreamService).openStream("AAPL");
	}

	private AuthResponse authResponse() {
		return new AuthResponse(
			"access-token",
			"Bearer",
			NOW.plusSeconds(900),
			"refresh-token",
			NOW.plusSeconds(604800),
			new CurrentUserResponse(USER_ID, "user@example.com", UserRole.USER)
		);
	}

	private OrderResponse orderResponse() {
		return new OrderResponse(
			UUID.randomUUID(),
			"AAPL",
			OrderSide.BUY,
			OrderType.MARKET,
			new BigDecimal("10.000000"),
			null,
			OrderStatus.PENDING,
			null,
			NOW,
			NOW
		);
	}

	private UsernamePasswordAuthenticationToken authenticatedUser(UUID userId) {
		AuthenticatedUser principal = new AuthenticatedUser(userId, "user@example.com", UserRole.USER);
		return new UsernamePasswordAuthenticationToken(
			principal,
			"token",
			List.of(new SimpleGrantedAuthority("ROLE_USER"))
		);
	}

	private String loginJson() {
		return """
			{
			  "email": "user@example.com",
			  "password": "Password123!"
			}
			""";
	}

	private String registerJson() {
		return """
			{
			  "email": "user@example.com",
			  "password": "Password123!"
			}
			""";
	}

	private String orderJson() {
		return """
			{
			  "symbol": "AAPL",
			  "side": "BUY",
			  "orderType": "MARKET",
			  "quantity": 10.000000
			}
			""";
	}
}
