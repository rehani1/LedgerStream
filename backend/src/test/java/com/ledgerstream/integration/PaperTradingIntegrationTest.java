package com.ledgerstream.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ledgerstream.auth.AuthService;
import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.auth.dto.AuthResponse;
import com.ledgerstream.auth.dto.RegisterRequest;
import com.ledgerstream.domain.model.Fill;
import com.ledgerstream.domain.model.LedgerEntry;
import com.ledgerstream.domain.model.LedgerEntryType;
import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.Position;
import com.ledgerstream.domain.model.PriceTick;
import com.ledgerstream.domain.model.RiskSnapshot;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.model.TradeOrder;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.FillRepository;
import com.ledgerstream.domain.repository.LedgerEntryRepository;
import com.ledgerstream.domain.repository.OrderRepository;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.domain.repository.PriceTickRepository;
import com.ledgerstream.domain.repository.RiskSnapshotRepository;
import com.ledgerstream.domain.repository.SymbolRepository;
import com.ledgerstream.events.EventPublisher;
import com.ledgerstream.events.OrderCreatedEvent;
import com.ledgerstream.events.OrderFilledEvent;
import com.ledgerstream.events.RiskUpdatedEvent;
import com.ledgerstream.orders.CreateOrderResult;
import com.ledgerstream.orders.OrderExecutionService;
import com.ledgerstream.orders.OrderService;
import com.ledgerstream.orders.dto.CreateOrderRequest;
import com.ledgerstream.portfolio.PortfolioQueryService;
import com.ledgerstream.portfolio.dto.LedgerPageResponse;
import com.ledgerstream.portfolio.dto.PortfolioPositionResponse;
import com.ledgerstream.portfolio.dto.PortfolioSummaryResponse;
import com.ledgerstream.quotes.CachedQuote;
import com.ledgerstream.quotes.RedisQuoteCacheService;
import com.ledgerstream.support.PostgresRepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Transactional
class PaperTradingIntegrationTest extends PostgresRepositoryTestSupport {

	private static final Instant QUOTE_TS = Instant.parse("2026-01-02T14:35:00Z");
	private static final BigDecimal BID = new BigDecimal("187.360000");
	private static final BigDecimal ASK = new BigDecimal("187.480000");
	private static final BigDecimal LAST = new BigDecimal("187.420000");

	@Autowired
	private AuthService authService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderExecutionService orderExecutionService;

	@Autowired
	private PortfolioQueryService portfolioQueryService;

	@Autowired
	private RedisQuoteCacheService quoteCacheService;

	@Autowired
	private SymbolRepository symbolRepository;

	@Autowired
	private PriceTickRepository priceTickRepository;

	@Autowired
	private PortfolioRepository portfolioRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private FillRepository fillRepository;

	@Autowired
	private PositionRepository positionRepository;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private RiskSnapshotRepository riskSnapshotRepository;

	@MockitoBean
	private EventPublisher eventPublisher;

	@Test
	void registeredUserCanCreateFillAndViewSettledPortfolio() {
		AuthResponse registered = registerUser("integration-trader@example.com");
		AuthenticatedUser principal = principal(registered);
		fundPortfolio(principal, new BigDecimal("10000.00"));
		seedAaplQuote();

		CreateOrderRequest request = marketBuy("AAPL", "10.000000");
		CreateOrderResult created = orderService.createOrder(principal, "integration-order-key-1", request, "integration-request-1");
		CreateOrderResult duplicate = orderService.createOrder(principal, "integration-order-key-1", request, "integration-request-duplicate");

		assertThat(created.created()).isTrue();
		assertThat(duplicate.created()).isFalse();
		assertThat(duplicate.order().id()).isEqualTo(created.order().id());
		assertThat(orderRepository.findByUserIdOrderByCreatedAtDesc(principal.id())).hasSize(1);

		TradeOrder order = orderRepository.findById(created.order().id()).orElseThrow();
		orderExecutionService.execute(order);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
		assertThat(portfolioRepository.findByUserId(principal.id()).orElseThrow().getCashBalance())
			.isEqualByComparingTo("8125.20");

		List<Fill> fills = fillRepository.findByOrderId(order.getId());
		assertThat(fills).hasSize(1);
		assertThat(fills.getFirst().getPrice()).isEqualByComparingTo("187.480000");
		assertThat(fills.getFirst().getQuantity()).isEqualByComparingTo("10.000000");

		Position position = positionRepository.findByUserIdAndSymbolTicker(principal.id(), "AAPL").orElseThrow();
		assertThat(position.getQuantity()).isEqualByComparingTo("10.000000");
		assertThat(position.getAvgCost()).isEqualByComparingTo("187.480000");
		assertThat(position.getRealizedPnl()).isEqualByComparingTo("0.00");

		List<LedgerEntry> ledgerEntries = ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(
			principal.id(),
			org.springframework.data.domain.PageRequest.of(0, 10)
		).getContent();
		assertThat(ledgerEntries).hasSize(1);
		assertThat(ledgerEntries.getFirst().getEntryType()).isEqualTo(LedgerEntryType.BUY_FILL);
		assertThat(ledgerEntries.getFirst().getCashDelta()).isEqualByComparingTo("-1874.80");
		assertThat(ledgerEntries.getFirst().getQuantityDelta()).isEqualByComparingTo("10.000000");

		PortfolioSummaryResponse portfolio = portfolioQueryService.getPortfolio(principal);
		assertThat(portfolio.cash()).isEqualByComparingTo("8125.20");
		assertThat(portfolio.marketValue()).isEqualByComparingTo("1874.20");
		assertThat(portfolio.totalEquity()).isEqualByComparingTo("9999.40");
		assertThat(portfolio.positionsCount()).isEqualTo(1);
		assertThat(portfolio.pricedPositionsCount()).isEqualTo(1);

		RiskSnapshot riskSnapshot = riskSnapshotRepository.findFirstByUserIdOrderByCreatedAtDesc(principal.id()).orElseThrow();
		assertThat(riskSnapshot.getTotalEquity()).isEqualByComparingTo("9999.40");
		assertThat(riskSnapshot.getGrossExposure()).isEqualByComparingTo("1874.20");

		verify(eventPublisher).publishOrderCreated(any(OrderCreatedEvent.class));
		verify(eventPublisher).publishOrderFilled(any(OrderFilledEvent.class));
		verify(eventPublisher).publishRiskUpdated(any(RiskUpdatedEvent.class));
	}

	@Test
	void userScopedQueriesDoNotExposeAnotherUsersOrdersPositionsOrLedger() {
		AuthResponse first = registerUser("isolation-owner@example.com");
		AuthResponse second = registerUser("isolation-other@example.com");
		AuthenticatedUser owner = principal(first);
		AuthenticatedUser other = principal(second);
		fundPortfolio(owner, new BigDecimal("5000.00"));
		seedAaplQuote();

		CreateOrderResult created = orderService.createOrder(
			owner,
			"isolation-order-key-1",
			marketBuy("AAPL", "2.000000"),
			"isolation-request-1"
		);
		orderExecutionService.execute(orderRepository.findById(created.order().id()).orElseThrow());

		assertThat(orderService.listOrders(other)).isEmpty();
		assertThatThrownBy(() -> orderService.getOrder(other, created.order().id()))
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));

		List<PortfolioPositionResponse> otherPositions = portfolioQueryService.listPositions(other);
		assertThat(otherPositions).isEmpty();

		LedgerPageResponse otherLedger = portfolioQueryService.listLedger(other, 0, 10);
		assertThat(otherLedger.entries()).isEmpty();
	}

	private AuthResponse registerUser(String email) {
		return authService.register(new RegisterRequest(email, "Password123!"), "integration-register");
	}

	private AuthenticatedUser principal(AuthResponse response) {
		return new AuthenticatedUser(response.user().id(), response.user().email(), UserRole.USER);
	}

	private void fundPortfolio(AuthenticatedUser principal, BigDecimal cash) {
		Portfolio portfolio = portfolioRepository.findByUserId(principal.id()).orElseThrow();
		portfolio.setCashBalance(cash);
		portfolioRepository.saveAndFlush(portfolio);
	}

	private void seedAaplQuote() {
		Symbol symbol = symbolRepository.findByTicker("AAPL").orElseThrow();
		PriceTick tick = new PriceTick();
		tick.setSymbol(symbol);
		tick.setTs(QUOTE_TS);
		tick.setBid(BID);
		tick.setAsk(ASK);
		tick.setLastPrice(LAST);
		tick.setVolume(1000L);
		tick.setSource("integration-test");
		priceTickRepository.saveAndFlush(tick);
		quoteCacheService.putLatestQuote(new CachedQuote("AAPL", QUOTE_TS, BID, ASK, LAST, 1000L, "integration-test"));
	}

	private CreateOrderRequest marketBuy(String symbol, String quantity) {
		return new CreateOrderRequest(symbol, OrderSide.BUY, OrderType.MARKET, new BigDecimal(quantity), null);
	}
}
