package com.ledgerstream.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.orders.dto.CreateOrderRequest;
import com.ledgerstream.orders.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

	private static final Instant CREATED_AT = Instant.parse("2026-01-02T14:35:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OrderService orderService;

	@Test
	void orderCreationRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/orders")
				.header("Idempotency-Key", "order-key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(marketOrderJson()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void createOrderReturnsCreatedForFirstSubmission() throws Exception {
		OrderResponse response = orderResponse(OrderStatus.PENDING);
		when(orderService.createOrder(any(AuthenticatedUser.class), eq("order-key-1"), any(CreateOrderRequest.class), eq("order-request-1")))
			.thenReturn(new CreateOrderResult(response, true));

		mockMvc.perform(post("/api/orders")
				.with(authentication(authenticatedUser()))
				.header("Idempotency-Key", "order-key-1")
				.header("X-Request-ID", "order-request-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(marketOrderJson()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(response.id().toString()))
			.andExpect(jsonPath("$.symbol").value("AAPL"))
			.andExpect(jsonPath("$.side").value("BUY"))
			.andExpect(jsonPath("$.orderType").value("MARKET"))
			.andExpect(jsonPath("$.quantity").value(10.000000))
			.andExpect(jsonPath("$.status").value("PENDING"));

		verify(orderService).createOrder(any(AuthenticatedUser.class), eq("order-key-1"), any(CreateOrderRequest.class), eq("order-request-1"));
	}

	@Test
	void duplicateIdempotencyKeyReturnsExistingOrderWithOk() throws Exception {
		OrderResponse response = orderResponse(OrderStatus.PENDING);
		when(orderService.createOrder(any(AuthenticatedUser.class), eq("order-key-1"), any(CreateOrderRequest.class), anyString()))
			.thenReturn(new CreateOrderResult(response, false));

		mockMvc.perform(post("/api/orders")
				.with(authentication(authenticatedUser()))
				.header("Idempotency-Key", "order-key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(marketOrderJson()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(response.id().toString()))
			.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void listOrdersReturnsUserScopedHistory() throws Exception {
		when(orderService.listOrders(any(AuthenticatedUser.class))).thenReturn(List.of(
			orderResponse(OrderStatus.PENDING),
			orderResponse(OrderStatus.CANCELLED)
		));

		mockMvc.perform(get("/api/orders").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].symbol").value("AAPL"))
			.andExpect(jsonPath("$[0].status").value("PENDING"))
			.andExpect(jsonPath("$[1].status").value("CANCELLED"));
	}

	@Test
	void getOrderReturnsDetail() throws Exception {
		OrderResponse response = orderResponse(OrderStatus.PENDING);
		when(orderService.getOrder(any(AuthenticatedUser.class), eq(response.id()))).thenReturn(response);

		mockMvc.perform(get("/api/orders/{id}", response.id()).with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(response.id().toString()))
			.andExpect(jsonPath("$.symbol").value("AAPL"));
	}

	@Test
	void cancelOrderReturnsUpdatedStatus() throws Exception {
		OrderResponse response = orderResponse(OrderStatus.CANCELLED);
		when(orderService.cancelOrder(any(AuthenticatedUser.class), eq(response.id()), eq("cancel-request-1"))).thenReturn(response);

		mockMvc.perform(post("/api/orders/{id}/cancel", response.id())
				.with(authentication(authenticatedUser()))
				.header("X-Request-ID", "cancel-request-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(response.id().toString()))
			.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void cancelOrderConflictReturnsCleanError() throws Exception {
		UUID orderId = UUID.randomUUID();
		when(orderService.cancelOrder(any(AuthenticatedUser.class), eq(orderId), anyString()))
			.thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be cancelled"));

		mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(authentication(authenticatedUser())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.message").value("Only pending orders can be cancelled"));
	}

	private String marketOrderJson() {
		return """
			{
			  "symbol": "AAPL",
			  "side": "BUY",
			  "orderType": "MARKET",
			  "quantity": 10.000000
			}
			""";
	}

	private OrderResponse orderResponse(OrderStatus status) {
		return new OrderResponse(
			UUID.randomUUID(),
			"AAPL",
			OrderSide.BUY,
			OrderType.MARKET,
			new BigDecimal("10.000000"),
			null,
			status,
			null,
			CREATED_AT,
			CREATED_AT
		);
	}

	private UsernamePasswordAuthenticationToken authenticatedUser() {
		AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), "user@example.com", UserRole.USER);
		return new UsernamePasswordAuthenticationToken(
			principal,
			"token",
			List.of(new SimpleGrantedAuthority("ROLE_USER"))
		);
	}
}
