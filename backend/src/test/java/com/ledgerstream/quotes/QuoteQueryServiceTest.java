package com.ledgerstream.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.PriceTick;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.repository.PriceTickRepository;
import com.ledgerstream.domain.repository.SymbolRepository;
import com.ledgerstream.quotes.dto.QuoteHistoryResponse;
import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.quotes.dto.SymbolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class QuoteQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");
	private static final Instant TICK_TS = Instant.parse("2026-01-02T14:34:00Z");

	@Mock
	private SymbolRepository symbolRepository;

	@Mock
	private PriceTickRepository priceTickRepository;

	@Mock
	private RedisQuoteCacheService quoteCacheService;

	private QuoteQueryService quoteQueryService;

	@BeforeEach
	void setUp() {
		quoteQueryService = new QuoteQueryService(
			symbolRepository,
			priceTickRepository,
			quoteCacheService,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void listSymbolsReturnsActiveSymbolsInRepositoryOrder() {
		Symbol aapl = symbol("AAPL");
		Symbol msft = symbol("MSFT");
		when(symbolRepository.findByActiveTrueOrderByTickerAsc()).thenReturn(List.of(aapl, msft));

		List<SymbolResponse> symbols = quoteQueryService.listSymbols();

		assertThat(symbols).extracting(SymbolResponse::ticker).containsExactly("AAPL", "MSFT");
		verify(symbolRepository).findByActiveTrueOrderByTickerAsc();
	}

	@Test
	void getLatestQuoteUsesRedisBeforeDatabaseFallback() {
		Symbol symbol = symbol("AAPL");
		CachedQuote cachedQuote = cachedQuote("AAPL");
		when(symbolRepository.findByTicker("AAPL")).thenReturn(Optional.of(symbol));
		when(quoteCacheService.getLatestQuote("AAPL")).thenReturn(Optional.of(cachedQuote));

		QuoteResponse quote = quoteQueryService.getLatestQuote(" aapl ");

		assertThat(quote.symbol()).isEqualTo("AAPL");
		assertThat(quote.timestamp()).isEqualTo(TICK_TS);
		assertThat(quote.last()).isEqualByComparingTo("187.420000");
		verify(priceTickRepository, never()).findFirstBySymbolTickerOrderByTsDesc(any());
	}

	@Test
	void getLatestQuoteFallsBackToDatabaseWhenCacheMisses() {
		Symbol symbol = symbol("MSFT");
		PriceTick tick = priceTick(symbol);
		when(symbolRepository.findByTicker("MSFT")).thenReturn(Optional.of(symbol));
		when(quoteCacheService.getLatestQuote("MSFT")).thenReturn(Optional.empty());
		when(priceTickRepository.findFirstBySymbolTickerOrderByTsDesc("MSFT")).thenReturn(Optional.of(tick));

		QuoteResponse quote = quoteQueryService.getLatestQuote("MSFT");

		assertThat(quote.symbol()).isEqualTo("MSFT");
		assertThat(quote.source()).isEqualTo("fixture");
		assertThat(quote.last()).isEqualByComparingTo("187.420000");
	}

	@Test
	void getLatestQuoteFallsBackToDatabaseWhenRedisFails() {
		Symbol symbol = symbol("NVDA");
		PriceTick tick = priceTick(symbol);
		when(symbolRepository.findByTicker("NVDA")).thenReturn(Optional.of(symbol));
		when(quoteCacheService.getLatestQuote("NVDA")).thenThrow(new IllegalStateException("redis down"));
		when(priceTickRepository.findFirstBySymbolTickerOrderByTsDesc("NVDA")).thenReturn(Optional.of(tick));

		QuoteResponse quote = quoteQueryService.getLatestQuote("NVDA");

		assertThat(quote.symbol()).isEqualTo("NVDA");
		assertThat(quote.timestamp()).isEqualTo(TICK_TS);
	}

	@Test
	void getHistoryUsesSupportedRangeAndRequestedLimit() {
		Symbol symbol = symbol("TSLA");
		PriceTick tick = priceTick(symbol);
		when(symbolRepository.findByTicker("TSLA")).thenReturn(Optional.of(symbol));
		when(priceTickRepository.findBySymbolTickerAndTsAfterOrderByTsAsc(any(), any(), any()))
			.thenReturn(List.of(tick));
		ArgumentCaptor<Instant> afterCaptor = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

		QuoteHistoryResponse history = quoteQueryService.getHistory("tsla", "1d", 25);

		verify(priceTickRepository)
			.findBySymbolTickerAndTsAfterOrderByTsAsc(
				org.mockito.ArgumentMatchers.eq("TSLA"),
				afterCaptor.capture(),
				pageableCaptor.capture()
			);
		assertThat(afterCaptor.getValue()).isEqualTo(Instant.parse("2026-01-01T14:35:00Z"));
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);
		assertThat(history.symbol()).isEqualTo("TSLA");
		assertThat(history.range()).isEqualTo("1d");
		assertThat(history.ticks()).hasSize(1);
	}

	@Test
	void missingSymbolReturnsNotFound() {
		when(symbolRepository.findByTicker("MISSING")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> quoteQueryService.getLatestQuote("missing"))
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode().value()).isEqualTo(404);
				assertThat(ex.getReason()).isEqualTo("Symbol not found");
			});

		verifyNoInteractions(quoteCacheService, priceTickRepository);
	}

	@Test
	void unsupportedHistoryRangeReturnsBadRequest() {
		when(symbolRepository.findByTicker("AAPL")).thenReturn(Optional.of(symbol("AAPL")));

		assertThatThrownBy(() -> quoteQueryService.getHistory("AAPL", "30d", 500))
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode().value()).isEqualTo(400);
				assertThat(ex.getReason()).contains("Unsupported history range");
			});
	}

	private CachedQuote cachedQuote(String ticker) {
		return new CachedQuote(
			ticker,
			TICK_TS,
			new BigDecimal("187.360000"),
			new BigDecimal("187.480000"),
			new BigDecimal("187.420000"),
			136_200L,
			"fixture"
		);
	}

	private PriceTick priceTick(Symbol symbol) {
		PriceTick tick = new PriceTick();
		tick.setSymbol(symbol);
		tick.setTs(TICK_TS);
		tick.setBid(new BigDecimal("187.360000"));
		tick.setAsk(new BigDecimal("187.480000"));
		tick.setLastPrice(new BigDecimal("187.420000"));
		tick.setVolume(136_200L);
		tick.setSource("fixture");
		return tick;
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
