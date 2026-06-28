package com.ledgerstream.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.model.TradeOrder;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.OrderRepository;
import com.ledgerstream.domain.repository.SymbolRepository;
import com.ledgerstream.domain.repository.UserRepository;
import com.ledgerstream.events.EventPublisher;
import com.ledgerstream.events.OrderCreatedEvent;
import com.ledgerstream.metrics.LedgerStreamMetrics;
import com.ledgerstream.orders.dto.CreateOrderRequest;
import com.ledgerstream.orders.dto.OrderResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final UUID SYMBOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

	@Mock
	private UserRepository userRepository;

	@Mock
	private SymbolRepository symbolRepository;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private EventPublisher eventPublisher;

	@Mock
	private AuditService auditService;

	private OrderService orderService;
	private SimpleMeterRegistry meterRegistry;
	private AuthenticatedUser authenticatedUser;
	private User user;
	private Symbol symbol;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		orderService = new OrderService(
			userRepository,
			symbolRepository,
			orderRepository,
			eventPublisher,
			auditService,
			new LedgerStreamMetrics(meterRegistry),
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		authenticatedUser = new AuthenticatedUser(USER_ID, "user@example.com", UserRole.USER);
		user = user();
		symbol = symbol("AAPL", true);
	}

	@Test
	void createMarketOrderPersistsPendingOrderAndPublishesEvent() {
		when(orderRepository.findByUserIdAndIdempotencyKey(USER_ID, "order-key-1")).thenReturn(Optional.empty());
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(symbolRepository.findByTicker("AAPL")).thenReturn(Optional.of(symbol));
		when(orderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> persist(invocation.getArgument(0)));

		CreateOrderResult result = orderService.createOrder(
			authenticatedUser,
			" order-key-1 ",
			new CreateOrderRequest("aapl", OrderSide.BUY, OrderType.MARKET, new BigDecimal("10.000000"), null),
			"request-1"
		);
		OrderResponse response = result.order();

		ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
		verify(orderRepository).save(orderCaptor.capture());
		TradeOrder savedOrder = orderCaptor.getValue();
		assertThat(savedOrder.getUser()).isEqualTo(user);
		assertThat(savedOrder.getSymbol()).isEqualTo(symbol);
		assertThat(savedOrder.getSide()).isEqualTo(OrderSide.BUY);
		assertThat(savedOrder.getOrderType()).isEqualTo(OrderType.MARKET);
		assertThat(savedOrder.getQuantity()).isEqualByComparingTo("10.000000");
		assertThat(savedOrder.getLimitPrice()).isNull();
		assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
		assertThat(savedOrder.getIdempotencyKey()).isEqualTo("order-key-1");

		ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
		verify(eventPublisher).publishOrderCreated(eventCaptor.capture());
		OrderCreatedEvent event = eventCaptor.getValue();
		assertThat(event.orderId()).isEqualTo(response.id());
		assertThat(event.userId()).isEqualTo(USER_ID);
		assertThat(event.symbol()).isEqualTo("AAPL");
		assertThat(event.side()).isEqualTo(OrderSide.BUY);
		assertThat(event.orderType()).isEqualTo(OrderType.MARKET);
		assertThat(event.quantity()).isEqualByComparingTo("10.000000");
		assertThat(event.limitPrice()).isNull();
		assertThat(event.requestId()).isEqualTo("request-1");
		assertThat(event.createdAt()).isEqualTo(NOW);

		verify(auditService).record(
			eq(user),
			eq("ORDER_CREATED"),
			eq("request-1"),
			eq(Map.of(
				"orderId", response.id().toString(),
				"symbol", "AAPL",
				"side", "BUY",
				"orderType", "MARKET",
				"quantity", new BigDecimal("10.000000")
			))
		);

		assertThat(response.symbol()).isEqualTo("AAPL");
		assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
		assertThat(response.createdAt()).isEqualTo(NOW);
		assertThat(result.created()).isTrue();
		assertThat(counter(LedgerStreamMetrics.ORDERS_CREATED)).isEqualTo(1.0);
	}

	@Test
	void duplicateIdempotencyKeyReturnsExistingOrderWithoutPublishingAgain() {
		TradeOrder existingOrder = order(OrderStatus.PENDING);
		when(orderRepository.findByUserIdAndIdempotencyKey(USER_ID, "order-key-2")).thenReturn(Optional.of(existingOrder));

		CreateOrderResult result = orderService.createOrder(
			authenticatedUser,
			"order-key-2",
			new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, new BigDecimal("10.000000"), null)
		);

		assertThat(result.order().id()).isEqualTo(existingOrder.getId());
		assertThat(result.created()).isFalse();
		verify(orderRepository, never()).save(any(TradeOrder.class));
		verify(userRepository, never()).findById(any(UUID.class));
		verify(eventPublisher, never()).publishOrderCreated(any(OrderCreatedEvent.class));
		verify(auditService, never()).record(any(), any(), any(), any());
		assertThat(counter(LedgerStreamMetrics.ORDERS_CREATED)).isZero();
	}

	@Test
	void createLimitOrderRequiresPositiveLimitPrice() {
		when(orderRepository.findByUserIdAndIdempotencyKey(USER_ID, "order-key-3")).thenReturn(Optional.empty());

		assertBadRequest(
			() -> orderService.createOrder(
				authenticatedUser,
				"order-key-3",
				new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("5.000000"), null)
			),
			"Limit price is required for limit orders"
		);

		verify(orderRepository, never()).save(any(TradeOrder.class));
		verify(eventPublisher, never()).publishOrderCreated(any(OrderCreatedEvent.class));
	}

	@Test
	void marketOrderRejectsLimitPrice() {
		when(orderRepository.findByUserIdAndIdempotencyKey(USER_ID, "order-key-4")).thenReturn(Optional.empty());

		assertBadRequest(
			() -> orderService.createOrder(
				authenticatedUser,
				"order-key-4",
				new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, new BigDecimal("5.000000"), new BigDecimal("180.00"))
			),
			"Market orders must not include a limit price"
		);
	}

	@Test
	void createOrderRejectsUnknownOrInactiveSymbol() {
		when(orderRepository.findByUserIdAndIdempotencyKey(USER_ID, "order-key-5")).thenReturn(Optional.empty());
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(symbolRepository.findByTicker("AAPL")).thenReturn(Optional.of(symbol("AAPL", false)));

		assertThatThrownBy(() -> orderService.createOrder(
			authenticatedUser,
			"order-key-5",
			new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, new BigDecimal("1.000000"), null)
		)).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
			assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(ex.getReason()).isEqualTo("Symbol not found");
		});

		verify(orderRepository, never()).save(any(TradeOrder.class));
	}

	@Test
	void cancelPendingOrderTransitionsToCancelled() {
		UUID orderId = UUID.randomUUID();
		TradeOrder pendingOrder = order(OrderStatus.PENDING);
		pendingOrder.setId(orderId);
		when(orderRepository.findByIdAndUserId(orderId, USER_ID)).thenReturn(Optional.of(pendingOrder));
		when(orderRepository.save(pendingOrder)).thenReturn(pendingOrder);

		OrderResponse response = orderService.cancelOrder(authenticatedUser, orderId, "request-cancel-1");

		assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		verify(orderRepository).save(pendingOrder);
		verify(auditService).record(
			eq(user),
			eq("ORDER_CANCELLED"),
			eq("request-cancel-1"),
			eq(Map.of(
				"orderId", orderId.toString(),
				"symbol", "AAPL",
				"side", "BUY",
				"orderType", "MARKET",
				"quantity", new BigDecimal("10.000000")
			))
		);
	}

	@Test
	void cancelFilledOrderFailsWithConflict() {
		UUID orderId = UUID.randomUUID();
		when(orderRepository.findByIdAndUserId(orderId, USER_ID)).thenReturn(Optional.of(order(OrderStatus.FILLED)));

		assertThatThrownBy(() -> orderService.cancelOrder(authenticatedUser, orderId))
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(ex.getReason()).isEqualTo("Only pending orders can be cancelled");
			});

		verify(orderRepository, never()).save(any(TradeOrder.class));
	}

	@Test
	void listOrdersReturnsUserScopedHistory() {
		TradeOrder newest = order(OrderStatus.PENDING);
		TradeOrder older = order(OrderStatus.CANCELLED);
		when(orderRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(newest, older));

		List<OrderResponse> orders = orderService.listOrders(authenticatedUser);

		assertThat(orders).extracting(OrderResponse::id).containsExactly(newest.getId(), older.getId());
	}

	@Test
	void getOrderReturnsUserScopedOrder() {
		UUID orderId = UUID.randomUUID();
		TradeOrder order = order(OrderStatus.PENDING);
		order.setId(orderId);
		when(orderRepository.findByIdAndUserId(orderId, USER_ID)).thenReturn(Optional.of(order));

		OrderResponse response = orderService.getOrder(authenticatedUser, orderId);

		assertThat(response.id()).isEqualTo(orderId);
		assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
	}

	@Test
	void getOrderReturnsNotFoundForMissingOrCrossUserOrder() {
		UUID orderId = UUID.randomUUID();
		when(orderRepository.findByIdAndUserId(orderId, USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orderService.getOrder(authenticatedUser, orderId))
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
				assertThat(ex.getReason()).isEqualTo("Order not found");
			});
	}

	private void assertBadRequest(Runnable action, String reason) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
				assertThat(ex.getReason()).isEqualTo(reason);
			});
	}

	private TradeOrder persist(TradeOrder order) {
		order.setId(UUID.randomUUID());
		order.setCreatedAt(NOW);
		order.setUpdatedAt(NOW);
		return order;
	}

	private TradeOrder order(OrderStatus status) {
		TradeOrder order = new TradeOrder();
		order.setId(UUID.randomUUID());
		order.setUser(user);
		order.setSymbol(symbol);
		order.setSide(OrderSide.BUY);
		order.setOrderType(OrderType.MARKET);
		order.setQuantity(new BigDecimal("10.000000"));
		order.setStatus(status);
		order.setIdempotencyKey("existing-key");
		order.setCreatedAt(NOW);
		order.setUpdatedAt(NOW);
		return order;
	}

	private User user() {
		User testUser = new User();
		testUser.setId(USER_ID);
		testUser.setEmail("user@example.com");
		testUser.setRole(UserRole.USER);
		testUser.setPasswordHash("hash");
		return testUser;
	}

	private Symbol symbol(String ticker, boolean active) {
		Symbol testSymbol = new Symbol();
		testSymbol.setId(SYMBOL_ID);
		testSymbol.setTicker(ticker);
		testSymbol.setName(ticker + " Inc.");
		testSymbol.setExchange("NASDAQ");
		testSymbol.setAssetType(AssetType.EQUITY);
		testSymbol.setCurrency("USD");
		testSymbol.setActive(active);
		return testSymbol;
	}

	private double counter(String name) {
		return meterRegistry.get(name).counter().count();
	}
}
