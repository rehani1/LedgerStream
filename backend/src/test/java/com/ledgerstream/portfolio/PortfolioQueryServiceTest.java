package com.ledgerstream.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.Fill;
import com.ledgerstream.domain.model.LedgerEntry;
import com.ledgerstream.domain.model.LedgerEntryType;
import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.Position;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.model.TradeOrder;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.LedgerEntryRepository;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.portfolio.dto.LedgerPageResponse;
import com.ledgerstream.portfolio.dto.PortfolioPositionResponse;
import com.ledgerstream.portfolio.dto.PortfolioSummaryResponse;
import com.ledgerstream.quotes.QuoteQueryService;
import com.ledgerstream.quotes.dto.QuoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PortfolioQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private PositionRepository positionRepository;

	@Mock
	private LedgerEntryRepository ledgerEntryRepository;

	@Mock
	private QuoteQueryService quoteQueryService;

	private PortfolioQueryService portfolioQueryService;
	private AuthenticatedUser authenticatedUser;
	private User user;
	private Symbol symbol;
	private Portfolio portfolio;

	@BeforeEach
	void setUp() {
		portfolioQueryService = new PortfolioQueryService(
			portfolioRepository,
			positionRepository,
			ledgerEntryRepository,
			quoteQueryService
		);
		authenticatedUser = new AuthenticatedUser(USER_ID, "user@example.com", UserRole.USER);
		user = user();
		symbol = symbol("AAPL");
		portfolio = portfolio(new BigDecimal("1000.00"));
	}

	@Test
	void getPortfolioUsesLatestQuotesForValuation() {
		Position position = position(new BigDecimal("5.000000"), new BigDecimal("100.000000"), new BigDecimal("12.34"));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserId(USER_ID)).thenReturn(List.of(position));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote(new BigDecimal("120.000000")));

		PortfolioSummaryResponse response = portfolioQueryService.getPortfolio(authenticatedUser);

		assertThat(response.cash()).isEqualByComparingTo("1000.00");
		assertThat(response.marketValue()).isEqualByComparingTo("600.00");
		assertThat(response.totalEquity()).isEqualByComparingTo("1600.00");
		assertThat(response.realizedPnl()).isEqualByComparingTo("12.34");
		assertThat(response.unrealizedPnl()).isEqualByComparingTo("100.00");
		assertThat(response.positionsCount()).isEqualTo(1);
		assertThat(response.pricedPositionsCount()).isEqualTo(1);
		verify(portfolioRepository).findByUserId(USER_ID);
		verify(positionRepository).findByUserId(USER_ID);
	}

	@Test
	void listPositionsFallsBackToCostBasisWhenQuoteIsMissing() {
		Position position = position(new BigDecimal("5.000000"), new BigDecimal("100.000000"), new BigDecimal("12.34"));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserId(USER_ID)).thenReturn(List.of(position));
		when(quoteQueryService.getLatestQuote("AAPL"))
			.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found"));

		List<PortfolioPositionResponse> response = portfolioQueryService.listPositions(authenticatedUser);

		assertThat(response).hasSize(1);
		PortfolioPositionResponse positionResponse = response.getFirst();
		assertThat(positionResponse.lastPrice()).isNull();
		assertThat(positionResponse.valuationPrice()).isEqualByComparingTo("100.000000");
		assertThat(positionResponse.valuationSource()).isEqualTo("COST_BASIS_FALLBACK");
		assertThat(positionResponse.marketValue()).isEqualByComparingTo("500.00");
		assertThat(positionResponse.costBasis()).isEqualByComparingTo("500.00");
		assertThat(positionResponse.unrealizedPnl()).isEqualByComparingTo("0.00");
		assertThat(positionResponse.realizedPnl()).isEqualByComparingTo("12.34");
	}

	@Test
	void listLedgerUsesUserScopedPagination() {
		LedgerEntry ledgerEntry = ledgerEntry();
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, PageRequest.of(0, 10)))
			.thenReturn(new PageImpl<>(List.of(ledgerEntry), PageRequest.of(0, 10), 1));

		LedgerPageResponse response = portfolioQueryService.listLedger(authenticatedUser, 0, 10);

		assertThat(response.page()).isEqualTo(0);
		assertThat(response.size()).isEqualTo(10);
		assertThat(response.totalElements()).isEqualTo(1);
		assertThat(response.entries()).hasSize(1);
		assertThat(response.entries().getFirst().entryType()).isEqualTo(LedgerEntryType.BUY_FILL);
		assertThat(response.entries().getFirst().cashDelta()).isEqualByComparingTo("-1874.80");
		verify(ledgerEntryRepository).findByUserIdOrderByCreatedAtDesc(USER_ID, PageRequest.of(0, 10));
	}

	@Test
	void listLedgerRejectsInvalidPageSize() {
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));

		assertThatThrownBy(() -> portfolioQueryService.listLedger(authenticatedUser, 0, 101))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Size must be between 1 and 100");
	}

	private QuoteResponse quote(BigDecimal last) {
		return new QuoteResponse("AAPL", NOW, last.subtract(new BigDecimal("0.050000")), last.add(new BigDecimal("0.050000")), last, 1000L, "fixture");
	}

	private LedgerEntry ledgerEntry() {
		TradeOrder order = order();
		Fill fill = fill(order);
		LedgerEntry entry = new LedgerEntry();
		entry.setId(UUID.randomUUID());
		entry.setUser(user);
		entry.setPortfolio(portfolio);
		entry.setOrder(order);
		entry.setFill(fill);
		entry.setEntryType(LedgerEntryType.BUY_FILL);
		entry.setCashDelta(new BigDecimal("-1874.80"));
		entry.setSymbol(symbol);
		entry.setQuantityDelta(new BigDecimal("10.000000"));
		entry.setPrice(new BigDecimal("187.480000"));
		entry.setCreatedAt(NOW);
		entry.setMetadata(new LinkedHashMap<>());
		entry.getMetadata().put("orderSide", "BUY");
		return entry;
	}

	private Fill fill(TradeOrder order) {
		Fill fill = new Fill();
		fill.setId(UUID.randomUUID());
		fill.setOrder(order);
		fill.setSymbol(symbol);
		fill.setPrice(new BigDecimal("187.480000"));
		fill.setQuantity(new BigDecimal("10.000000"));
		fill.setFee(new BigDecimal("0.00"));
		fill.setFilledAt(NOW);
		return fill;
	}

	private TradeOrder order() {
		TradeOrder order = new TradeOrder();
		order.setId(UUID.randomUUID());
		order.setUser(user);
		order.setSymbol(symbol);
		order.setSide(OrderSide.BUY);
		order.setOrderType(OrderType.MARKET);
		order.setQuantity(new BigDecimal("10.000000"));
		order.setStatus(OrderStatus.FILLED);
		order.setIdempotencyKey("order-key-1");
		order.setCreatedAt(NOW);
		order.setUpdatedAt(NOW);
		return order;
	}

	private Portfolio portfolio(BigDecimal cash) {
		Portfolio testPortfolio = new Portfolio();
		testPortfolio.setId(UUID.randomUUID());
		testPortfolio.setUser(user);
		testPortfolio.setCashBalance(cash);
		testPortfolio.setBaseCurrency("USD");
		testPortfolio.setCreatedAt(NOW);
		testPortfolio.setUpdatedAt(NOW);
		return testPortfolio;
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
