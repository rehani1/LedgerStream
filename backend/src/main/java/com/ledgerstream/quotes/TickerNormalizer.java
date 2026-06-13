package com.ledgerstream.quotes;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class TickerNormalizer {

	private static final int MAX_STREAM_SYMBOLS = 25;
	private static final Pattern TICKER_PATTERN = Pattern.compile("[A-Z0-9.]{1,16}");

	private TickerNormalizer() {
	}

	static String normalizeTicker(String ticker) {
		if (ticker == null || ticker.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticker is required");
		}
		String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
		if (!TICKER_PATTERN.matcher(normalizedTicker).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticker is invalid");
		}
		return normalizedTicker;
	}

	static List<String> normalizeTickerList(String symbols) {
		if (symbols == null || symbols.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one symbol is required");
		}
		Set<String> normalizedSymbols = new LinkedHashSet<>();
		Arrays.stream(symbols.split(","))
			.map(TickerNormalizer::normalizeTicker)
			.forEach(normalizedSymbols::add);
		if (normalizedSymbols.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one symbol is required");
		}
		if (normalizedSymbols.size() > MAX_STREAM_SYMBOLS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many symbols requested");
		}
		return List.copyOf(normalizedSymbols);
	}
}
