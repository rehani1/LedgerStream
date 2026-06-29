package com.ledgerstream.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.PriceTick;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.repository.PriceTickRepository;
import com.ledgerstream.domain.repository.SymbolRepository;
import com.ledgerstream.events.MarketTickEvent;
import com.ledgerstream.orders.OrderExecutionService;
import com.ledgerstream.portfolio.PortfolioSnapshotService;
import com.ledgerstream.quotes.CachedQuote;
import com.ledgerstream.quotes.QuoteStreamService;
import com.ledgerstream.quotes.RedisQuoteCacheService;
import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.risk.RiskCalculationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketTickIngestionServiceTest {

	private static final Instant TIMESTAMP = Instant.parse("2026-01-01T14:30:00Z");

	@Mock
	private SymbolRepository symbolRepository;

	@Mock
	private PriceTickRepository priceTickRepository;

	@Mock
	private RedisQuoteCacheService quoteCacheService;

	@Mock
	private QuoteStreamService quoteStreamService;

	@Mock
	private RiskCalculationService riskCalculationService;

	@Mock
	private PortfolioSnapshotService portfolioSnapshotService;

	@Mock
	private OrderExecutionService orderExecutionService;

	private SimpleMeterRegistry meterRegistry;
	private MarketTickIngestionService ingestionService;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		ingestionService = new MarketTickIngestionService(
			symbolRepository,
			priceTickRepository,
			quoteCacheService,
			quoteStreamService,
			riskCalculationService,
			portfolioSnapshotService,
			orderExecutionService,
			meterRegistry
		);
	}

	@Test
	void ingestPersistsAndCachesValidMarketTick() {
		Symbol symbol = symbol("AAPL");
		MarketTickEvent event = marketTick(" aapl ");
		when(symbolRepository.findByTicker("AAPL")).thenReturn(Optional.of(symbol));
		when(priceTickRepository.existsBySymbolIdAndTsAndSource(symbol.getId(), TIMESTAMP, "fixture"))
			.thenReturn(false);
		ArgumentCaptor<PriceTick> priceTickCaptor = ArgumentCaptor.forClass(PriceTick.class);
		ArgumentCaptor<CachedQuote> quoteCaptor = ArgumentCaptor.forClass(CachedQuote.class);

		ingestionService.ingest(event);

		verify(priceTickRepository).save(priceTickCaptor.capture());
		PriceTick priceTick = priceTickCaptor.getValue();
		assertThat(priceTick.getSymbol()).isSameAs(symbol);
		assertThat(priceTick.getTs()).isEqualTo(TIMESTAMP);
		assertThat(priceTick.getBid()).isEqualByComparingTo("187.120000");
		assertThat(priceTick.getAsk()).isEqualByComparingTo("187.180000");
		assertThat(priceTick.getLastPrice()).isEqualByComparingTo("187.150000");
		assertThat(priceTick.getVolume()).isEqualTo(1_000L);
		assertThat(priceTick.getSource()).isEqualTo("fixture");

		verify(quoteCacheService).putLatestQuote(quoteCaptor.capture());
		CachedQuote cachedQuote = quoteCaptor.getValue();
		assertThat(cachedQuote.symbol()).isEqualTo("AAPL");
		assertThat(cachedQuote.timestamp()).isEqualTo(TIMESTAMP);
		assertThat(cachedQuote.last()).isEqualByComparingTo("187.150000");
		verify(quoteStreamService).broadcast(any(QuoteResponse.class));
		verify(riskCalculationService).recordSnapshotsForSymbol("AAPL");
		verify(portfolioSnapshotService).recordSnapshotsForSymbol("AAPL");
		verify(orderExecutionService).executePendingLimitOrders("AAPL");
		assertThat(counter("ledgerstream_market_ticks_consumed_total")).isEqualTo(1.0);
		assertThat(counter("ledgerstream_market_ticks_failed_total")).isZero();
	}

	@Test
	void ingestRefreshesCacheButSkipsPersistingDuplicateTick() {
		Symbol symbol = symbol("MSFT");
		MarketTickEvent event = marketTick("MSFT");
		when(symbolRepository.findByTicker("MSFT")).thenReturn(Optional.of(symbol));
		when(priceTickRepository.existsBySymbolIdAndTsAndSource(symbol.getId(), TIMESTAMP, "fixture"))
			.thenReturn(true);

		ingestionService.ingest(event);

		verify(priceTickRepository, never()).save(any());
		verify(quoteCacheService).putLatestQuote(any(CachedQuote.class));
		verify(quoteStreamService).broadcast(any(QuoteResponse.class));
		verify(riskCalculationService).recordSnapshotsForSymbol("MSFT");
		verify(portfolioSnapshotService).recordSnapshotsForSymbol("MSFT");
		verify(orderExecutionService).executePendingLimitOrders("MSFT");
		assertThat(counter("ledgerstream_market_ticks_consumed_total")).isEqualTo(1.0);
		assertThat(counter("ledgerstream_market_ticks_failed_total")).isZero();
	}

	@Test
	void ingestRejectsUnknownSymbolAndCountsFailure() {
		MarketTickEvent event = marketTick("MISSING");
		when(symbolRepository.findByTicker("MISSING")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ingestionService.ingest(event))
			.isInstanceOf(MarketTickRejectedException.class)
			.hasMessage("Unknown market data symbol: MISSING");

		verify(priceTickRepository, never()).save(any());
		verifyNoInteractions(quoteCacheService);
		verifyNoInteractions(riskCalculationService);
		verifyNoInteractions(portfolioSnapshotService);
		verifyNoInteractions(orderExecutionService);
		assertThat(counter("ledgerstream_market_ticks_consumed_total")).isZero();
		assertThat(counter("ledgerstream_market_ticks_failed_total")).isEqualTo(1.0);
	}

	@Test
	void ingestRejectsInvalidTickBeforeRepositoryWork() {
		MarketTickEvent event = new MarketTickEvent(
			UUID.randomUUID(),
			"AAPL",
			TIMESTAMP,
			new BigDecimal("187.180000"),
			new BigDecimal("187.120000"),
			new BigDecimal("187.150000"),
			1_000L,
			"fixture"
		);

		assertThatThrownBy(() -> ingestionService.ingest(event))
			.isInstanceOf(MarketTickRejectedException.class)
			.hasMessage("Market tick ask must be greater than or equal to bid");

		verifyNoInteractions(symbolRepository, priceTickRepository, quoteCacheService, riskCalculationService);
		verifyNoInteractions(portfolioSnapshotService);
		verifyNoInteractions(orderExecutionService);
		assertThat(counter("ledgerstream_market_ticks_consumed_total")).isZero();
		assertThat(counter("ledgerstream_market_ticks_failed_total")).isEqualTo(1.0);
	}

	private double counter(String name) {
		return meterRegistry.get(name).counter().count();
	}

	private MarketTickEvent marketTick(String symbol) {
		return new MarketTickEvent(
			UUID.randomUUID(),
			symbol,
			TIMESTAMP,
			new BigDecimal("187.120000"),
			new BigDecimal("187.180000"),
			new BigDecimal("187.150000"),
			1_000L,
			"fixture"
		);
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
}
