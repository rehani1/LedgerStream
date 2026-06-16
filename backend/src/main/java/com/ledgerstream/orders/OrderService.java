package com.ledgerstream.orders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

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
	private final Clock clock;

	public OrderService(
		UserRepository userRepository,
		SymbolRepository symbolRepository,
		OrderRepository orderRepository,
		EventPublisher eventPublisher,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.symbolRepository = symbolRepository;
		this.orderRepository = orderRepository;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public OrderResponse createOrder(
		AuthenticatedUser authenticatedUser,
		String idempotencyKey,
		CreateOrderRequest request
	) {
		String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
		return orderRepository.findByUserIdAndIdempotencyKey(authenticatedUser.id(), normalizedIdempotencyKey)
			.map(OrderResponse::from)
			.orElseGet(() -> createNewOrder(authenticatedUser, normalizedIdempotencyKey, request));
	}

	@Transactional
	public OrderResponse cancelOrder(AuthenticatedUser authenticatedUser, UUID orderId) {
		TradeOrder order = orderRepository.findByIdAndUserId(orderId, authenticatedUser.id())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		if (order.getStatus() != OrderStatus.PENDING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be cancelled");
		}
		order.setStatus(OrderStatus.CANCELLED);
		return OrderResponse.from(orderRepository.save(order));
	}

	private OrderResponse createNewOrder(
		AuthenticatedUser authenticatedUser,
		String idempotencyKey,
		CreateOrderRequest request
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
		eventPublisher.publishOrderCreated(toOrderCreatedEvent(savedOrder));
		return OrderResponse.from(savedOrder);
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

	private OrderCreatedEvent toOrderCreatedEvent(TradeOrder order) {
		return new OrderCreatedEvent(
			UUID.randomUUID(),
			order.getId(),
			order.getUser().getId(),
			order.getSymbol().getTicker(),
			order.getSide(),
			order.getOrderType(),
			order.getQuantity(),
			order.getLimitPrice(),
			order.getCreatedAt() == null ? Instant.now(clock) : order.getCreatedAt()
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
