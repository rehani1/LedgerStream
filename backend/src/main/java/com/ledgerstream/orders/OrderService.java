package com.ledgerstream.orders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.model.TradeOrder;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.repository.OrderRepository;
import com.ledgerstream.domain.repository.SymbolRepository;
import com.ledgerstream.domain.repository.UserRepository;
import com.ledgerstream.events.EventPublisher;
import com.ledgerstream.events.OrderCreatedEvent;
import com.ledgerstream.orders.dto.CreateOrderRequest;
import com.ledgerstream.orders.dto.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

	private static final Pattern TICKER_PATTERN = Pattern.compile("[A-Z0-9.]{1,16}");
	private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

	private final UserRepository userRepository;
	private final SymbolRepository symbolRepository;
	private final OrderRepository orderRepository;
	private final EventPublisher eventPublisher;
	private final AuditService auditService;
	private final Clock clock;

	public OrderService(
		UserRepository userRepository,
		SymbolRepository symbolRepository,
		OrderRepository orderRepository,
		EventPublisher eventPublisher,
		AuditService auditService,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.symbolRepository = symbolRepository;
		this.orderRepository = orderRepository;
		this.eventPublisher = eventPublisher;
		this.auditService = auditService;
		this.clock = clock;
	}

	@Transactional
	public CreateOrderResult createOrder(
		AuthenticatedUser authenticatedUser,
		String idempotencyKey,
		CreateOrderRequest request
	) {
		return createOrder(authenticatedUser, idempotencyKey, request, null);
	}

	@Transactional
	public CreateOrderResult createOrder(
		AuthenticatedUser authenticatedUser,
		String idempotencyKey,
		CreateOrderRequest request,
		String requestId
	) {
		String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
		return orderRepository.findByUserIdAndIdempotencyKey(authenticatedUser.id(), normalizedIdempotencyKey)
			.map(order -> new CreateOrderResult(OrderResponse.from(order), false))
			.orElseGet(() -> createNewOrder(authenticatedUser, normalizedIdempotencyKey, request, requestId));
	}

	@Transactional
	public OrderResponse cancelOrder(AuthenticatedUser authenticatedUser, UUID orderId) {
		return cancelOrder(authenticatedUser, orderId, null);
	}

	@Transactional
	public OrderResponse cancelOrder(AuthenticatedUser authenticatedUser, UUID orderId, String requestId) {
		TradeOrder order = orderRepository.findByIdAndUserId(orderId, authenticatedUser.id())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		if (order.getStatus() != OrderStatus.PENDING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be cancelled");
		}
		order.setStatus(OrderStatus.CANCELLED);
		TradeOrder savedOrder = orderRepository.save(order);
		auditService.record(savedOrder.getUser(), "ORDER_CANCELLED", requestId, orderMetadata(savedOrder));
		return OrderResponse.from(savedOrder);
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> listOrders(AuthenticatedUser authenticatedUser) {
		return orderRepository.findByUserIdOrderByCreatedAtDesc(authenticatedUser.id()).stream()
			.map(OrderResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public OrderResponse getOrder(AuthenticatedUser authenticatedUser, UUID orderId) {
		return orderRepository.findByIdAndUserId(orderId, authenticatedUser.id())
			.map(OrderResponse::from)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
	}

	private CreateOrderResult createNewOrder(
		AuthenticatedUser authenticatedUser,
		String idempotencyKey,
		CreateOrderRequest request,
		String requestId
	) {
		validateRequest(request);
		User user = userRepository.findById(authenticatedUser.id())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
		Symbol symbol = symbolRepository.findByTicker(normalizeTicker(request.symbol()))
			.filter(Symbol::isActive)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Symbol not found"));

		TradeOrder order = new TradeOrder();
		order.setUser(user);
		order.setSymbol(symbol);
		order.setSide(request.side());
		order.setOrderType(request.orderType());
		order.setQuantity(request.quantity());
		order.setLimitPrice(request.orderType() == OrderType.LIMIT ? request.limitPrice() : null);
		order.setStatus(OrderStatus.PENDING);
		order.setIdempotencyKey(idempotencyKey);

		TradeOrder savedOrder = orderRepository.save(order);
		auditService.record(savedOrder.getUser(), "ORDER_CREATED", requestId, orderMetadata(savedOrder));
		eventPublisher.publishOrderCreated(toOrderCreatedEvent(savedOrder, requestId));
		return new CreateOrderResult(OrderResponse.from(savedOrder), true);
	}

	private void validateRequest(CreateOrderRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order request is required");
		}
		normalizeTicker(request.symbol());
		if (request.side() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order side is required");
		}
		if (request.orderType() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order type is required");
		}
		if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
		}
		if (request.orderType() == OrderType.LIMIT) {
			if (request.limitPrice() == null || request.limitPrice().compareTo(BigDecimal.ZERO) <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit price is required for limit orders");
			}
			return;
		}
		if (request.limitPrice() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Market orders must not include a limit price");
		}
	}

	private OrderCreatedEvent toOrderCreatedEvent(TradeOrder order, String requestId) {
		return new OrderCreatedEvent(
			UUID.randomUUID(),
			order.getId(),
			order.getUser().getId(),
			order.getSymbol().getTicker(),
			order.getSide(),
			order.getOrderType(),
			order.getQuantity(),
			order.getLimitPrice(),
			requestId,
			order.getCreatedAt() == null ? Instant.now(clock) : order.getCreatedAt()
		);
	}

	private Map<String, Object> orderMetadata(TradeOrder order) {
		return Map.of(
			"orderId", order.getId().toString(),
			"symbol", order.getSymbol().getTicker(),
			"side", order.getSide().name(),
			"orderType", order.getOrderType().name(),
			"quantity", order.getQuantity()
		);
	}

	private String normalizeTicker(String ticker) {
		if (ticker == null || ticker.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticker is required");
		}
		String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
		if (!TICKER_PATTERN.matcher(normalizedTicker).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticker is invalid");
		}
		return normalizedTicker;
	}

	private String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency key is required");
		}
		String normalizedKey = idempotencyKey.trim();
		if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalizedKey).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency key is invalid");
		}
		return normalizedKey;
	}
}
