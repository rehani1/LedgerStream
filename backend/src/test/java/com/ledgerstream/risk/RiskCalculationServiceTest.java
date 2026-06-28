package com.ledgerstream.risk;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.Position;
import com.ledgerstream.domain.model.RiskSnapshot;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.domain.repository.RiskSnapshotRepository;
import com.ledgerstream.events.EventPublisher;
import com.ledgerstream.events.RiskUpdatedEvent;
import com.ledgerstream.quotes.QuoteQueryService;
import com.ledgerstream.quotes.dto.QuoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RiskCalculationServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private PositionRepository positionRepository;

	@Mock
	private RiskSnapshotRepository riskSnapshotRepository;

	@Mock
	private QuoteQueryService quoteQueryService;

	@Mock
	private EventPublisher eventPublisher;

	private RiskCalculationService riskCalculationService;
	private User user;

	@BeforeEach
	void setUp() {
		riskCalculationService = new RiskCalculationService(
			portfolioRepository,
			positionRepository,
			riskSnapshotRepository,
			quoteQueryService,
			eventPublisher,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		user = user(USER_ID);
	}

	@Test
	void recordSnapshotCalculatesExposureConcentrationAndUnrealizedPnl() {
		Portfolio portfolio = portfolio(user, new BigDecimal("1000.00"));
		Position apple = position(user, symbol("AAPL"), new BigDecimal("5.000000"), new BigDecimal("100.000000"));
		Position microsoft = position(user, symbol("MSFT"), new BigDecimal("2.000000"), new BigDecimal("200.000000"));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserId(USER_ID)).thenReturn(List.of(apple, microsoft));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote("AAPL", new BigDecimal("120.000000")));
		when(quoteQueryService.getLatestQuote("MSFT")).thenReturn(quote("MSFT", new BigDecimal("220.000000")));
		when(riskSnapshotRepository.save(any(RiskSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RiskSnapshot snapshot = riskCalculationService.recordSnapshot(USER_ID);

		assertThat(snapshot.getTotalEquity()).isEqualByComparingTo("2040.00");
		assertThat(snapshot.getCash()).isEqualByComparingTo("1000.00");
		assertThat(snapshot.getGrossExposure()).isEqualByComparingTo("1040.00");
		assertThat(snapshot.getLargestPositionPct()).isEqualByComparingTo("29.4118");
		assertThat(snapshot.getUnrealizedPnl()).isEqualByComparingTo("140.00");
		assertThat(snapshot.getCreatedAt()).isEqualTo(NOW);

		ArgumentCaptor<RiskUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(RiskUpdatedEvent.class);
		verify(eventPublisher).publishRiskUpdated(eventCaptor.capture());
		RiskUpdatedEvent event = eventCaptor.getValue();
		assertThat(event.userId()).isEqualTo(USER_ID);
		assertThat(event.totalEquity()).isEqualByComparingTo("2040.00");
		assertThat(event.grossExposure()).isEqualByComparingTo("1040.00");
		assertThat(event.largestPositionPct()).isEqualByComparingTo("29.4118");
		assertThat(event.unrealizedPnl()).isEqualByComparingTo("140.00");
		assertThat(event.createdAt()).isEqualTo(NOW);
	}

	@Test
	void recordSnapshotFallsBackToCostBasisWhenLatestQuoteIsMissing() {
		Portfolio portfolio = portfolio(user, new BigDecimal("100.00"));
		Position position = position(user, symbol("AAPL"), new BigDecimal("4.000000"), new BigDecimal("50.000000"));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserId(USER_ID)).thenReturn(List.of(position));
		when(quoteQueryService.getLatestQuote("AAPL"))
			.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found"));
		when(riskSnapshotRepository.save(any(RiskSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RiskSnapshot snapshot = riskCalculationService.recordSnapshot(USER_ID);

		assertThat(snapshot.getTotalEquity()).isEqualByComparingTo("300.00");
		assertThat(snapshot.getGrossExposure()).isEqualByComparingTo("200.00");
		assertThat(snapshot.getLargestPositionPct()).isEqualByComparingTo("66.6667");
		assertThat(snapshot.getUnrealizedPnl()).isEqualByComparingTo("0.00");
	}

	@Test
	void recordSnapshotsForSymbolDeduplicatesAffectedUsersAndIgnoresClosedPositions() {
		User secondUser = user(UUID.fromString("00000000-0000-0000-0000-000000000202"));
		Portfolio portfolio = portfolio(user, new BigDecimal("500.00"));
		Position openPosition = position(user, symbol("AAPL"), new BigDecimal("2.000000"), new BigDecimal("100.000000"));
		Position duplicateUserPosition = position(user, symbol("AAPL"), new BigDecimal("1.000000"), new BigDecimal("110.000000"));
		Position closedPosition = position(secondUser, symbol("AAPL"), new BigDecimal("0.000000"), new BigDecimal("90.000000"));
		when(positionRepository.findBySymbolTicker("AAPL")).thenReturn(List.of(openPosition, duplicateUserPosition, closedPosition));
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(positionRepository.findByUserId(USER_ID)).thenReturn(List.of(openPosition, duplicateUserPosition));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quote("AAPL", new BigDecimal("125.000000")));
		when(riskSnapshotRepository.save(any(RiskSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

		List<RiskSnapshot> snapshots = riskCalculationService.recordSnapshotsForSymbol("AAPL");

		assertThat(snapshots).hasSize(1);
		assertThat(snapshots.getFirst().getUser().getId()).isEqualTo(USER_ID);
		assertThat(snapshots.getFirst().getGrossExposure()).isEqualByComparingTo("375.00");
	}

	private QuoteResponse quote(String ticker, BigDecimal last) {
		return new QuoteResponse(ticker, NOW, last.subtract(new BigDecimal("0.050000")), last.add(new BigDecimal("0.050000")), last, 1000L, "fixture");
	}

	private Portfolio portfolio(User owner, BigDecimal cash) {
		Portfolio portfolio = new Portfolio();
		portfolio.setId(UUID.randomUUID());
		portfolio.setUser(owner);
		portfolio.setCashBalance(cash);
		portfolio.setBaseCurrency("USD");
		return portfolio;
	}

	private Position position(User owner, Symbol symbol, BigDecimal quantity, BigDecimal avgCost) {
		Position position = new Position();
		position.setId(UUID.randomUUID());
		position.setUser(owner);
		position.setSymbol(symbol);
		position.setQuantity(quantity);
		position.setAvgCost(avgCost);
		position.setRealizedPnl(new BigDecimal("0.00"));
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
