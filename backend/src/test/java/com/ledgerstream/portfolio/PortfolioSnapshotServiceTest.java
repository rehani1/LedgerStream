package com.ledgerstream.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.PortfolioSnapshot;
import com.ledgerstream.domain.model.Position;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PortfolioSnapshotRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.portfolio.dto.PortfolioHistoryResponse;
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
class PortfolioSnapshotServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private PositionRepository positionRepository;

	@Mock
	private PortfolioSnapshotRepository portfolioSnapshotRepository;

	@Mock
	private QuoteQueryService quoteQueryService;

	private PortfolioSnapshotService snapshotService;
	private AuthenticatedUser authenticatedUser;
	private User user;

	@BeforeEach
	void setUp() {
		snapshotService = new PortfolioSnapshotService(
			portfolioRepository,
			positionRepository,
			portfolioSnapshotRepository,
			quoteQueryService,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		authenticatedUser = new AuthenticatedUser(USER_ID, "user@example.com", UserRole.USER);
		user = user(USER_ID);
	}

	@Test
	void recordSnapshotStoresPortfolioMetricsWithLatestQuotes() {
		Portfolio portfolio = portfolio(user, new BigDecimal("1000.00"));
		Position position = position(user, symbol("AAPL"), new BigDecimal("5.000000"), new BigDecimal("100.000000"), new BigDecimal("12.34"));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserId(USER_ID)).thenReturn(List.of(position));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote("AAPL", new BigDecimal("120.000000")));
		when(portfolioSnapshotRepository.save(any(PortfolioSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PortfolioSnapshot snapshot = snapshotService.recordSnapshot(USER_ID);

		assertThat(snapshot.getUser()).isSameAs(user);
		assertThat(snapshot.getPortfolio()).isSameAs(portfolio);
		assertThat(snapshot.getCash()).isEqualByComparingTo("1000.00");
		assertThat(snapshot.getMarketValue()).isEqualByComparingTo("600.00");
		assertThat(snapshot.getGrossExposure()).isEqualByComparingTo("600.00");
		assertThat(snapshot.getTotalEquity()).isEqualByComparingTo("1600.00");
		assertThat(snapshot.getRealizedPnl()).isEqualByComparingTo("12.34");
		assertThat(snapshot.getUnrealizedPnl()).isEqualByComparingTo("100.00");
		assertThat(snapshot.getCreatedAt()).isEqualTo(NOW);
		verify(portfolioSnapshotRepository).save(snapshot);
	}

	@Test
	void recordSnapshotFallsBackToCostBasisWhenLatestQuoteIsMissing() {
		Portfolio portfolio = portfolio(user, new BigDecimal("100.00"));
		Position position = position(user, symbol("AAPL"), new BigDecimal("4.000000"), new BigDecimal("50.000000"), new BigDecimal("0.00"));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserId(USER_ID)).thenReturn(List.of(position));
		when(quoteQueryService.getLatestQuote("AAPL"))
			.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found"));
		when(portfolioSnapshotRepository.save(any(PortfolioSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PortfolioSnapshot snapshot = snapshotService.recordSnapshot(USER_ID);

		assertThat(snapshot.getMarketValue()).isEqualByComparingTo("200.00");
		assertThat(snapshot.getGrossExposure()).isEqualByComparingTo("200.00");
		assertThat(snapshot.getTotalEquity()).isEqualByComparingTo("300.00");
		assertThat(snapshot.getUnrealizedPnl()).isEqualByComparingTo("0.00");
	}

	@Test
	void recordSnapshotsForSymbolDeduplicatesAffectedUsersAndIgnoresClosedPositions() {
		User secondUser = user(UUID.fromString("00000000-0000-0000-0000-000000000202"));
		Portfolio portfolio = portfolio(user, new BigDecimal("500.00"));
		Symbol apple = symbol("AAPL");
		Position openPosition = position(user, apple, new BigDecimal("2.000000"), new BigDecimal("100.000000"), new BigDecimal("0.00"));
		Position duplicateUserPosition = position(user, apple, new BigDecimal("1.000000"), new BigDecimal("110.000000"), new BigDecimal("0.00"));
		Position closedPosition = position(secondUser, apple, new BigDecimal("0.000000"), new BigDecimal("90.000000"), new BigDecimal("0.00"));
		when(positionRepository.findBySymbolTicker("AAPL")).thenReturn(List.of(openPosition, duplicateUserPosition, closedPosition));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserId(USER_ID)).thenReturn(List.of(openPosition, duplicateUserPosition));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote("AAPL", new BigDecimal("125.000000")));
		when(portfolioSnapshotRepository.save(any(PortfolioSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

		List<PortfolioSnapshot> snapshots = snapshotService.recordSnapshotsForSymbol("AAPL");

		assertThat(snapshots).hasSize(1);
		assertThat(snapshots.getFirst().getUser().getId()).isEqualTo(USER_ID);
		assertThat(snapshots.getFirst().getMarketValue()).isEqualByComparingTo("375.00");
		assertThat(snapshots.getFirst().getUnrealizedPnl()).isEqualByComparingTo("65.00");
	}

	@Test
	void listHistoryUsesUserScopedPagination() {
		Portfolio portfolio = portfolio(user, new BigDecimal("1000.00"));
		PortfolioSnapshot snapshot = snapshot(portfolio);
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(portfolioSnapshotRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, PageRequest.of(0, 25)))
			.thenReturn(new PageImpl<>(List.of(snapshot), PageRequest.of(0, 25), 1));

		PortfolioHistoryResponse response = snapshotService.listHistory(authenticatedUser, 0, 25);

		assertThat(response.page()).isEqualTo(0);
		assertThat(response.size()).isEqualTo(25);
		assertThat(response.totalElements()).isEqualTo(1);
		assertThat(response.snapshots()).hasSize(1);
		assertThat(response.snapshots().getFirst().portfolioId()).isEqualTo(portfolio.getId());
		assertThat(response.snapshots().getFirst().totalEquity()).isEqualByComparingTo("1600.00");
		verify(portfolioSnapshotRepository).findByUserIdOrderByCreatedAtDesc(USER_ID, PageRequest.of(0, 25));
	}

	@Test
	void listHistoryRejectsInvalidPageSize() {
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio(user, new BigDecimal("1000.00"))));

		assertThatThrownBy(() -> snapshotService.listHistory(authenticatedUser, 0, 101))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Size must be between 1 and 100");
	}

	private QuoteResponse quote(String ticker, BigDecimal last) {
		return new QuoteResponse(ticker, NOW, last.subtract(new BigDecimal("0.050000")), last.add(new BigDecimal("0.050000")), last, 1000L, "fixture");
	}

	private PortfolioSnapshot snapshot(Portfolio portfolio) {
		PortfolioSnapshot snapshot = new PortfolioSnapshot();
		snapshot.setId(UUID.randomUUID());
		snapshot.setUser(portfolio.getUser());
		snapshot.setPortfolio(portfolio);
		snapshot.setCash(new BigDecimal("1000.00"));
		snapshot.setMarketValue(new BigDecimal("600.00"));
		snapshot.setGrossExposure(new BigDecimal("600.00"));
		snapshot.setTotalEquity(new BigDecimal("1600.00"));
		snapshot.setRealizedPnl(new BigDecimal("12.34"));
		snapshot.setUnrealizedPnl(new BigDecimal("100.00"));
		snapshot.setCreatedAt(NOW);
		return snapshot;
	}

	private Portfolio portfolio(User owner, BigDecimal cash) {
		Portfolio portfolio = new Portfolio();
		portfolio.setId(UUID.randomUUID());
		portfolio.setUser(owner);
		portfolio.setCashBalance(cash);
		portfolio.setBaseCurrency("USD");
		return portfolio;
	}

	private Position position(User owner, Symbol symbol, BigDecimal quantity, BigDecimal avgCost, BigDecimal realizedPnl) {
		Position position = new Position();
		position.setId(UUID.randomUUID());
		position.setUser(owner);
		position.setSymbol(symbol);
		position.setQuantity(quantity);
		position.setAvgCost(avgCost);
		position.setRealizedPnl(realizedPnl);
		position.setUpdatedAt(NOW);
		return position;
	}

	private Symbol symbol(String ticker) {
		Symbol symbol = new Symbol();
		symbol.setId(UUID.randomUUID());
		symbol.setTicker(ticker);
		symbol.setName(ticker + " Inc.");
		symbol.setExchange("NASDAQ");
		symbol.setAssetType(AssetType.EQUITY);
		symbol.setCurrency("USD");
		symbol.setActive(true);
		return symbol;
	}

	private User user(UUID id) {
		User testUser = new User();
		testUser.setId(id);
		testUser.setEmail(id + "@example.com");
		testUser.setRole(UserRole.USER);
		testUser.setPasswordHash("hash");
		return testUser;
	}
}
