package com.ledgerstream.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

import com.ledgerstream.domain.model.PriceTick;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.repository.PriceTickRepository;
import com.ledgerstream.domain.repository.SymbolRepository;
import com.ledgerstream.events.MarketTickEvent;
import com.ledgerstream.orders.OrderExecutionService;
import com.ledgerstream.quotes.CachedQuote;
import com.ledgerstream.quotes.QuoteStreamService;
import com.ledgerstream.quotes.RedisQuoteCacheService;
import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.risk.RiskCalculationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketTickIngestionService {

	private final SymbolRepository symbolRepository;
	private final PriceTickRepository priceTickRepository;
	private final RedisQuoteCacheService quoteCacheService;
	private final QuoteStreamService quoteStreamService;
	private final RiskCalculationService riskCalculationService;
	private final OrderExecutionService orderExecutionService;
	private final Counter consumedCounter;
	private final Counter failedCounter;

	public MarketTickIngestionService(
		SymbolRepository symbolRepository,
		PriceTickRepository priceTickRepository,
		RedisQuoteCacheService quoteCacheService,
		QuoteStreamService quoteStreamService,
		RiskCalculationService riskCalculationService,
		OrderExecutionService orderExecutionService,
		MeterRegistry meterRegistry
	) {
		this.symbolRepository = symbolRepository;
		this.priceTickRepository = priceTickRepository;
		this.quoteCacheService = quoteCacheService;
		this.quoteStreamService = quoteStreamService;
		this.riskCalculationService = riskCalculationService;
		this.orderExecutionService = orderExecutionService;
		this.consumedCounter = Counter.builder("ledgerstream_market_ticks_consumed_total")
			.description("Market tick events consumed and applied by the backend")
			.register(meterRegistry);
		this.failedCounter = Counter.builder("ledgerstream_market_ticks_failed_total")
			.description("Market tick events rejected or failed during backend ingestion")
			.register(meterRegistry);
	}

	@Transactional
	public void ingest(MarketTickEvent event) {
		try {
			ValidatedTick tick = validate(event);
			Symbol symbol = symbolRepository.findByTicker(tick.symbol())
				.orElseThrow(() -> new MarketTickRejectedException("Unknown market data symbol: " + tick.symbol()));

			if (!priceTickRepository.existsBySymbolIdAndTsAndSource(symbol.getId(), tick.timestamp(), tick.source())) {
				priceTickRepository.save(toPriceTick(symbol, tick));
			}

			CachedQuote cachedQuote = new CachedQuote(
				tick.symbol(),
				tick.timestamp(),
				tick.bid(),
				tick.ask(),
				tick.last(),
				tick.volume(),
				tick.source()
			);
			quoteCacheService.putLatestQuote(cachedQuote);
			quoteStreamService.broadcast(QuoteResponse.from(cachedQuote));
			riskCalculationService.recordSnapshotsForSymbol(tick.symbol());
			orderExecutionService.executePendingLimitOrders(tick.symbol());
			consumedCounter.increment();
		} catch (MarketTickRejectedException ex) {
			failedCounter.increment();
			throw ex;
		} catch (RuntimeException ex) {
			failedCounter.increment();
			throw ex;
		}
	}

	private PriceTick toPriceTick(Symbol symbol, ValidatedTick tick) {
		PriceTick priceTick = new PriceTick();
		priceTick.setSymbol(symbol);
		priceTick.setTs(tick.timestamp());
		priceTick.setBid(tick.bid());
		priceTick.setAsk(tick.ask());
		priceTick.setLastPrice(tick.last());
		priceTick.setVolume(tick.volume());
		priceTick.setSource(tick.source());
		return priceTick;
	}

	private ValidatedTick validate(MarketTickEvent event) {
		if (event == null) {
			throw new MarketTickRejectedException("Market tick event is required");
		}
		String symbol = normalizeRequired(event.symbol(), "symbol").toUpperCase(Locale.ROOT);
		String source = normalizeRequired(event.source(), "source");
		Instant timestamp = require(event.timestamp(), "timestamp");
		BigDecimal last = positive(require(event.last(), "last"), "last");
		BigDecimal bid = optionalPositive(event.bid(), "bid");
		BigDecimal ask = optionalPositive(event.ask(), "ask");
		if (bid != null && ask != null && ask.compareTo(bid) < 0) {
			throw new MarketTickRejectedException("Market tick ask must be greater than or equal to bid");
		}
		if (event.volume() != null && event.volume() < 0) {
			throw new MarketTickRejectedException("Market tick volume must be greater than or equal to zero");
		}
		return new ValidatedTick(symbol, timestamp, bid, ask, last, event.volume(), source);
	}

	private String normalizeRequired(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new MarketTickRejectedException("Market tick " + field + " is required");
		}
		return value.trim();
	}

	private <T> T require(T value, String field) {
		if (value == null) {
			throw new MarketTickRejectedException("Market tick " + field + " is required");
		}
		return value;
	}

	private BigDecimal optionalPositive(BigDecimal value, String field) {
		return value == null ? null : positive(value, field);
	}

	private BigDecimal positive(BigDecimal value, String field) {
		if (value.signum() <= 0) {
			throw new MarketTickRejectedException("Market tick " + field + " must be greater than zero");
		}
		return value;
	}

	private record ValidatedTick(
		String symbol,
		Instant timestamp,
		BigDecimal bid,
		BigDecimal ask,
		BigDecimal last,
		Long volume,
		String source
	) {
	}
}
