package com.ledgerstream.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.Fill;
import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.Position;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.model.TradeOrder;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.FillRepository;
import com.ledgerstream.domain.repository.OrderRepository;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.events.EventPublisher;
import com.ledgerstream.events.OrderCreatedEvent;
import com.ledgerstream.events.OrderFilledEvent;
import com.ledgerstream.portfolio.PortfolioLedgerService;
import com.ledgerstream.quotes.QuoteQueryService;
import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.risk.RiskCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private FillRepository fillRepository;

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private PositionRepository positionRepository;

	@Mock
	private QuoteQueryService quoteQueryService;

	@Mock
	private PortfolioLedgerService portfolioLedgerService;

	@Mock
	private RiskCalculationService riskCalculationService;

	@Mock
	private EventPublisher eventPublisher;

	@Mock
	private AuditService auditService;

	private OrderExecutionService executionService;
	private User user;
	private Symbol symbol;

	@BeforeEach
	void setUp() {
		executionService = new OrderExecutionService(
			orderRepository,
			fillRepository,
			portfolioRepository,
			positionRepository,
			quoteQueryService,
			portfolioLedgerService,
			riskCalculationService,
			eventPublisher,
			auditService,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		user = user();
		symbol = symbol("AAPL");
	}

	@Test
	void marketBuyUsesAskCreatesFillMarksFilledAndPublishesEvent() {
		TradeOrder order = order(OrderSide.BUY, OrderType.MARKET, OrderStatus.PENDING, new BigDecimal("10.000000"));
		Portfolio portfolio = portfolio(new BigDecimal("5000.00"));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote(
			new BigDecimal("187.360000"),
			new BigDecimal("187.480000"),
			new BigDecimal("187.420000")
		));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserIdAndSymbolTicker(USER_ID, "AAPL")).thenReturn(Optional.empty());
		when(orderRepository.save(order)).thenReturn(order);
		when(fillRepository.save(any(Fill.class))).thenAnswer(invocation -> persistFill(invocation.getArgument(0)));

		executionService.execute(order);

		ArgumentCaptor<Fill> fillCaptor = ArgumentCaptor.forClass(Fill.class);
		verify(fillRepository).save(fillCaptor.capture());
		Fill fill = fillCaptor.getValue();
		assertThat(fill.getOrder()).isEqualTo(order);
		assertThat(fill.getSymbol()).isEqualTo(symbol);
		assertThat(fill.getPrice()).isEqualByComparingTo("187.480000");
		assertThat(fill.getQuantity()).isEqualByComparingTo("10.000000");
		assertThat(fill.getFee()).isEqualByComparingTo("0.00");
		assertThat(fill.getFilledAt()).isEqualTo(NOW);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
		assertThat(portfolio.getCashBalance()).isEqualByComparingTo("3125.20");

		ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);
		verify(positionRepository).save(positionCaptor.capture());
		Position position = positionCaptor.getValue();
		assertThat(position.getUser()).isEqualTo(user);
		assertThat(position.getSymbol()).isEqualTo(symbol);
		assertThat(position.getQuantity()).isEqualByComparingTo("10.000000");
		assertThat(position.getAvgCost()).isEqualByComparingTo("187.480000");
		assertThat(position.getRealizedPnl()).isEqualByComparingTo("0.00");
		verify(portfolioRepository).save(portfolio);
		verify(portfolioLedgerService).appendFill(
			portfolio,
			fill,
			new BigDecimal("-1874.80"),
			new BigDecimal("10.000000")
		);
		verify(riskCalculationService).recordSnapshot(USER_ID);

		ArgumentCaptor<OrderFilledEvent> eventCaptor = ArgumentCaptor.forClass(OrderFilledEvent.class);
		verify(eventPublisher).publishOrderFilled(eventCaptor.capture());
		OrderFilledEvent event = eventCaptor.getValue();
		assertThat(event.orderId()).isEqualTo(order.getId());
		assertThat(event.fillId()).isEqualTo(fill.getId());
		assertThat(event.userId()).isEqualTo(USER_ID);
		assertThat(event.symbol()).isEqualTo("AAPL");
		assertThat(event.side()).isEqualTo(OrderSide.BUY);
		assertThat(event.quantity()).isEqualByComparingTo("10.000000");
		assertThat(event.price()).isEqualByComparingTo("187.480000");
		assertThat(event.filledAt()).isEqualTo(NOW);
	}

	@Test
	void marketBuyFallsBackToLastPriceWhenAskIsMissing() {
		TradeOrder order = order(OrderSide.BUY, OrderType.MARKET, OrderStatus.PENDING, new BigDecimal("1.000000"));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote(new BigDecimal("187.360000"), null, new BigDecimal("187.420000")));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio(new BigDecimal("5000.00"))));
		when(positionRepository.findByUserIdAndSymbolTicker(USER_ID, "AAPL")).thenReturn(Optional.empty());
		when(orderRepository.save(order)).thenReturn(order);
		when(fillRepository.save(any(Fill.class))).thenAnswer(invocation -> persistFill(invocation.getArgument(0)));

		executionService.execute(order);

		ArgumentCaptor<Fill> fillCaptor = ArgumentCaptor.forClass(Fill.class);
		verify(fillRepository).save(fillCaptor.capture());
		assertThat(fillCaptor.getValue().getPrice()).isEqualByComparingTo("187.420000");
	}

	@Test
	void marketBuyRecalculatesWeightedAverageCostForExistingPosition() {
		TradeOrder order = order(OrderSide.BUY, OrderType.MARKET, OrderStatus.PENDING, new BigDecimal("3.000000"));
		Portfolio portfolio = portfolio(new BigDecimal("1000.00"));
		Position position = position(
			new BigDecimal("2.000000"),
			new BigDecimal("100.000000"),
			new BigDecimal("12.34")
		);
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote(
			new BigDecimal("159.900000"),
			new BigDecimal("160.000000"),
			new BigDecimal("159.950000")
		));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserIdAndSymbolTicker(USER_ID, "AAPL")).thenReturn(Optional.of(position));
		when(orderRepository.save(order)).thenReturn(order);
		when(fillRepository.save(any(Fill.class))).thenAnswer(invocation -> persistFill(invocation.getArgument(0)));

		executionService.execute(order);

		assertThat(portfolio.getCashBalance()).isEqualByComparingTo("520.00");
		assertThat(position.getQuantity()).isEqualByComparingTo("5.000000");
		assertThat(position.getAvgCost()).isEqualByComparingTo("136.000000");
		assertThat(position.getRealizedPnl()).isEqualByComparingTo("12.34");
		verify(positionRepository).save(position);
		verify(portfolioRepository).save(portfolio);
	}

	@Test
	void marketSellUsesBidAndSettlesPartialSaleWhenSharesAreAvailable() {
		TradeOrder order = order(OrderSide.SELL, OrderType.MARKET, OrderStatus.PENDING, new BigDecimal("4.000000"));
		Portfolio portfolio = portfolio(new BigDecimal("1000.00"));
		Position position = position(new BigDecimal("5.000000"));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote(
			new BigDecimal("187.360000"),
			new BigDecimal("187.480000"),
			new BigDecimal("187.420000")
		));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserIdAndSymbolTicker(USER_ID, "AAPL")).thenReturn(Optional.of(position));
		when(orderRepository.save(order)).thenReturn(order);
		when(fillRepository.save(any(Fill.class))).thenAnswer(invocation -> persistFill(invocation.getArgument(0)));

		executionService.execute(order);

		ArgumentCaptor<Fill> fillCaptor = ArgumentCaptor.forClass(Fill.class);
		verify(fillRepository).save(fillCaptor.capture());
		assertThat(fillCaptor.getValue().getPrice()).isEqualByComparingTo("187.360000");
		assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
		assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1749.44");
		assertThat(position.getQuantity()).isEqualByComparingTo("1.000000");
		assertThat(position.getAvgCost()).isEqualByComparingTo("100.000000");
		assertThat(position.getRealizedPnl()).isEqualByComparingTo("349.44");
		verify(positionRepository).save(position);
		verify(portfolioRepository).save(portfolio);
		verify(portfolioLedgerService).appendFill(
			portfolio,
			fillCaptor.getValue(),
			new BigDecimal("749.44"),
			new BigDecimal("-4.000000")
		);
		verify(eventPublisher).publishOrderFilled(any(OrderFilledEvent.class));
	}

	@Test
	void marketSellResetsAverageCostAfterFullSale() {
		TradeOrder order = order(OrderSide.SELL, OrderType.MARKET, OrderStatus.PENDING, new BigDecimal("5.000000"));
		Portfolio portfolio = portfolio(new BigDecimal("1000.00"));
		Position position = position(new BigDecimal("5.000000"));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote(
			new BigDecimal("125.000000"),
			new BigDecimal("125.100000"),
			new BigDecimal("125.050000")
		));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserIdAndSymbolTicker(USER_ID, "AAPL")).thenReturn(Optional.of(position));
		when(orderRepository.save(order)).thenReturn(order);
		when(fillRepository.save(any(Fill.class))).thenAnswer(invocation -> persistFill(invocation.getArgument(0)));

		executionService.execute(order);

		assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1625.00");
		assertThat(position.getQuantity()).isEqualByComparingTo("0.000000");
		assertThat(position.getAvgCost()).isEqualByComparingTo("0.000000");
		assertThat(position.getRealizedPnl()).isEqualByComparingTo("125.00");
		verify(positionRepository).save(position);
		verify(portfolioRepository).save(portfolio);
	}

	@Test
	void insufficientCashRejectsOrderWithoutFill() {
		TradeOrder order = order(OrderSide.BUY, OrderType.MARKET, OrderStatus.PENDING, new BigDecimal("10.000000"));
		OrderCreatedEvent event = orderCreatedEvent(order, "request-reject-1");
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote(
			new BigDecimal("187.360000"),
			new BigDecimal("187.480000"),
			new BigDecimal("187.420000")
		));
		when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio(new BigDecimal("100.00"))));
		when(orderRepository.save(order)).thenReturn(order);

		executionService.execute(event);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
		assertThat(order.getRejectionReason()).isEqualTo("Insufficient cash");
		verify(auditService).record(
			eq(user),
			eq("ORDER_REJECTED"),
			eq("request-reject-1"),
			eq(Map.of(
				"orderId", order.getId().toString(),
				"symbol", "AAPL",
				"side", "BUY",
				"orderType", "MARKET",
				"quantity", new BigDecimal("10.000000"),
				"reason", "Insufficient cash"
			))
		);
		verify(fillRepository, never()).save(any(Fill.class));
		verify(portfolioLedgerService, never()).appendFill(any(), any(), any(), any());
		verify(riskCalculationService, never()).recordSnapshot(any());
		verify(eventPublisher, never()).publishOrderFilled(any(OrderFilledEvent.class));
	}

	@Test
	void missingPortfolioRejectsOrderWithoutFill() {
		TradeOrder order = order(OrderSide.BUY, OrderType.MARKET, OrderStatus.PENDING, new BigDecimal("1.000000"));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote(
			new BigDecimal("187.360000"),
			new BigDecimal("187.480000"),
			new BigDecimal("187.420000")
		));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
		when(orderRepository.save(order)).thenReturn(order);

		executionService.execute(order);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
		assertThat(order.getRejectionReason()).isEqualTo("Portfolio not found");
		verify(fillRepository, never()).save(any(Fill.class));
		verify(positionRepository, never()).save(any(Position.class));
		verify(portfolioLedgerService, never()).appendFill(any(), any(), any(), any());
		verify(riskCalculationService, never()).recordSnapshot(any());
		verify(eventPublisher, never()).publishOrderFilled(any(OrderFilledEvent.class));
	}

	@Test
	void insufficientSharesRejectsOrderWithoutFill() {
		TradeOrder order = order(OrderSide.SELL, OrderType.MARKET, OrderStatus.PENDING, new BigDecimal("10.000000"));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote(
			new BigDecimal("187.360000"),
			new BigDecimal("187.480000"),
			new BigDecimal("187.420000")
		));
		when(positionRepository.findByUserIdAndSymbolTicker(USER_ID, "AAPL")).thenReturn(Optional.of(position(new BigDecimal("2.000000"))));
		when(orderRepository.save(order)).thenReturn(order);

		executionService.execute(order);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
		assertThat(order.getRejectionReason()).isEqualTo("Insufficient shares");
		verify(fillRepository, never()).save(any(Fill.class));
		verify(portfolioLedgerService, never()).appendFill(any(), any(), any(), any());
		verify(riskCalculationService, never()).recordSnapshot(any());
		verify(eventPublisher, never()).publishOrderFilled(any(OrderFilledEvent.class));
	}

	@Test
	void missingQuoteRejectsOrder() {
		TradeOrder order = order(OrderSide.BUY, OrderType.MARKET, OrderStatus.PENDING, new BigDecimal("1.000000"));
		when(quoteQueryService.getLatestQuote("AAPL"))
			.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No quote available"));
		when(orderRepository.save(order)).thenReturn(order);

		executionService.execute(order);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
		assertThat(order.getRejectionReason()).isEqualTo("No market quote available");
		verify(fillRepository, never()).save(any(Fill.class));
		verify(portfolioLedgerService, never()).appendFill(any(), any(), any(), any());
		verify(riskCalculationService, never()).recordSnapshot(any());
	}

	@Test
	void limitOrdersRemainPendingForLaterExecutionFlow() {
		TradeOrder order = order(OrderSide.BUY, OrderType.LIMIT, OrderStatus.PENDING, new BigDecimal("1.000000"));

		executionService.execute(order);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
		verify(quoteQueryService, never()).getLatestQuote("AAPL");
		verify(orderRepository, never()).save(any(TradeOrder.class));
	}

	@Test
	void executeEventLooksUpOrderById() {
		TradeOrder order = order(OrderSide.BUY, OrderType.LIMIT, OrderStatus.PENDING, new BigDecimal("1.000000"));
		OrderCreatedEvent event = new OrderCreatedEvent(
			UUID.randomUUID(),
			order.getId(),
			USER_ID,
			"AAPL",
			OrderSide.BUY,
			OrderType.LIMIT,
			new BigDecimal("1.000000"),
			new BigDecimal("180.000000"),
			"request-event-1",
			NOW
		);
		when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

		executionService.execute(event);

		verify(orderRepository).findById(order.getId());
	}

	private OrderCreatedEvent orderCreatedEvent(TradeOrder order, String requestId) {
		return new OrderCreatedEvent(
			UUID.randomUUID(),
			order.getId(),
			USER_ID,
			order.getSymbol().getTicker(),
			order.getSide(),
			order.getOrderType(),
			order.getQuantity(),
			order.getLimitPrice(),
			requestId,
			order.getCreatedAt()
		);
	}

	private Fill persistFill(Fill fill) {
		fill.setId(UUID.randomUUID());
		return fill;
	}

	private QuoteResponse quote(BigDecimal bid, BigDecimal ask, BigDecimal last) {
		return new QuoteResponse("AAPL", NOW, bid, ask, last, 1000L, "fixture");
	}

	private Portfolio portfolio(BigDecimal cash) {
		Portfolio portfolio = new Portfolio();
		portfolio.setId(UUID.randomUUID());
		portfolio.setUser(user);
		portfolio.setCashBalance(cash);
		portfolio.setBaseCurrency("USD");
		return portfolio;
	}

	private Position position(BigDecimal quantity) {
		return position(quantity, new BigDecimal("100.000000"), new BigDecimal("0.00"));
	}

	private Position position(BigDecimal quantity, BigDecimal avgCost, BigDecimal realizedPnl) {
		Position position = new Position();
		position.setId(UUID.randomUUID());
		position.setUser(user);
		position.setSymbol(symbol);
		position.setQuantity(quantity);
		position.setAvgCost(avgCost);
		position.setRealizedPnl(realizedPnl);
		position.setUpdatedAt(NOW);
		return position;
	}

	private TradeOrder order(OrderSide side, OrderType orderType, OrderStatus status, BigDecimal quantity) {
		TradeOrder order = new TradeOrder();
		order.setId(UUID.randomUUID());
		order.setUser(user);
		order.setSymbol(symbol);
		order.setSide(side);
		order.setOrderType(orderType);
		order.setQuantity(quantity);
		order.setLimitPrice(orderType == OrderType.LIMIT ? new BigDecimal("180.000000") : null);
		order.setStatus(status);
		order.setIdempotencyKey("order-key-1");
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

	private Symbol symbol(String ticker) {
		Symbol testSymbol = new Symbol();
		testSymbol.setId(UUID.randomUUID());
		testSymbol.setTicker(ticker);
		testSymbol.setName(ticker + " Inc.");
		testSymbol.setExchange("NASDAQ");
		testSymbol.setAssetType(AssetType.EQUITY);
		testSymbol.setCurrency("USD");
		testSymbol.setActive(true);
		return testSymbol;
	}
}
