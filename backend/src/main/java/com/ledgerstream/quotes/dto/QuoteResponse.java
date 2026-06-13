package com.ledgerstream.quotes.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ledgerstream.domain.model.PriceTick;
import com.ledgerstream.quotes.CachedQuote;

public record QuoteResponse(
	String symbol,
	Instant timestamp,
	BigDecimal bid,
	BigDecimal ask,
	BigDecimal last,
	Long volume,
	String source
) {

	public static QuoteResponse from(CachedQuote quote) {
		return new QuoteResponse(
			quote.symbol(),
			quote.timestamp(),
			quote.bid(),
			quote.ask(),
			quote.last(),
			quote.volume(),
			quote.source()
		);
	}

	public static QuoteResponse from(PriceTick tick) {
		return new QuoteResponse(
			tick.getSymbol().getTicker(),
			tick.getTs(),
			tick.getBid(),
			tick.getAsk(),
			tick.getLastPrice(),
			tick.getVolume(),
			tick.getSource()
		);
	}
}
