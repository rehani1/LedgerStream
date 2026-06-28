package com.ledgerstream.quotes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.ledgerstream.domain.model.PriceTick;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.repository.PriceTickRepository;
import com.ledgerstream.domain.repository.SymbolRepository;
import com.ledgerstream.metrics.LedgerStreamMetrics;
import com.ledgerstream.quotes.dto.QuoteHistoryResponse;
import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.quotes.dto.SymbolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuoteQueryService {

	private static final Logger log = LoggerFactory.getLogger(QuoteQueryService.class);
	private static final Map<String, Duration> SUPPORTED_RANGES = Map.of(
		"5m", Duration.ofMinutes(5),
		"15m", Duration.ofMinutes(15),
		"1h", Duration.ofHours(1),
		"6h", Duration.ofHours(6),
		"1d", Duration.ofDays(1),
		"5d", Duration.ofDays(5)
	);

	private final SymbolRepository symbolRepository;
	private final PriceTickRepository priceTickRepository;
	private final RedisQuoteCacheService quoteCacheService;
	private final LedgerStreamMetrics metrics;
	private final Clock clock;

	public QuoteQueryService(
		SymbolRepository symbolRepository,
		PriceTickRepository priceTickRepository,
		RedisQuoteCacheService quoteCacheService,
		LedgerStreamMetrics metrics,
		Clock clock
	) {
		this.symbolRepository = symbolRepository;
		this.priceTickRepository = priceTickRepository;
		this.quoteCacheService = quoteCacheService;
		this.metrics = metrics;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<SymbolResponse> listSymbols() {
		return symbolRepository.findByActiveTrueOrderByTickerAsc().stream()
			.map(SymbolResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public SymbolResponse getSymbol(String ticker) {
		return SymbolResponse.from(requireSymbol(ticker));
	}

	@Transactional(readOnly = true)
	public QuoteResponse getLatestQuote(String ticker) {
		String normalizedTicker = TickerNormalizer.normalizeTicker(ticker);
		requireSymbol(normalizedTicker);

		Optional<CachedQuote> cachedQuote = latestQuoteFromCache(normalizedTicker);
		if (cachedQuote.isPresent()) {
			metrics.recordQuoteCacheHit();
			return QuoteResponse.from(cachedQuote.get());
		}
		metrics.recordQuoteCacheMiss();

		return priceTickRepository.findFirstBySymbolTickerOrderByTsDesc(normalizedTicker)
			.map(QuoteResponse::from)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found"));
	}

	@Transactional(readOnly = true)
	public QuoteHistoryResponse getHistory(String ticker, String range, int limit) {
		String normalizedTicker = TickerNormalizer.normalizeTicker(ticker);
		requireSymbol(normalizedTicker);
		String normalizedRange = normalizeRange(range);
		Instant after = Instant.now(clock).minus(SUPPORTED_RANGES.get(normalizedRange));
		List<QuoteResponse> ticks = priceTickRepository
			.findBySymbolTickerAndTsAfterOrderByTsAsc(normalizedTicker, after, PageRequest.of(0, limit))
			.stream()
			.map(QuoteResponse::from)
			.toList();
		return new QuoteHistoryResponse(normalizedTicker, normalizedRange, limit, ticks);
	}

	private Optional<CachedQuote> latestQuoteFromCache(String ticker) {
		try {
			return quoteCacheService.getLatestQuote(ticker);
		} catch (RuntimeException ex) {
			log.warn("Latest quote cache lookup failed for symbol {}", ticker, ex);
			return Optional.empty();
		}
	}

	private Symbol requireSymbol(String ticker) {
		String normalizedTicker = TickerNormalizer.normalizeTicker(ticker);
		return symbolRepository.findByTicker(normalizedTicker)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Symbol not found"));
	}

	private String normalizeRange(String range) {
		String normalizedRange = (range == null || range.isBlank()) ? "1d" : range.trim().toLowerCase(Locale.ROOT);
		if (!SUPPORTED_RANGES.containsKey(normalizedRange)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Unsupported history range. Supported values: 5m, 15m, 1h, 6h, 1d, 5d"
			);
		}
		return normalizedRange;
	}
}
