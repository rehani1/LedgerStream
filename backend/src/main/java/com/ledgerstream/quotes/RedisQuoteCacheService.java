package com.ledgerstream.quotes;

import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerstream.config.properties.RedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisQuoteCacheService {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final RedisProperties properties;

	public RedisQuoteCacheService(
		StringRedisTemplate redisTemplate,
		ObjectMapper objectMapper,
		RedisProperties properties
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	public void putLatestQuote(CachedQuote quote) {
		CachedQuote normalizedQuote = new CachedQuote(
			normalizeSymbol(quote.symbol()),
			quote.timestamp(),
			quote.bid(),
			quote.ask(),
			quote.last(),
			quote.volume(),
			quote.source()
		);
		redisTemplate.opsForValue().set(latestQuoteKey(normalizedQuote.symbol()), writeJson(normalizedQuote));
	}

	public Optional<CachedQuote> getLatestQuote(String symbol) {
		String value = redisTemplate.opsForValue().get(latestQuoteKey(symbol));
		if (value == null) {
			return Optional.empty();
		}
		return Optional.of(readJson(value));
	}

	public String latestQuoteKey(String symbol) {
		return properties.latestQuoteKeyPrefix() + ":" + normalizeSymbol(symbol);
	}

	private String writeJson(CachedQuote quote) {
		try {
			return objectMapper.writeValueAsString(quote);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize cached quote", ex);
		}
	}

	private CachedQuote readJson(String value) {
		try {
			return objectMapper.readValue(value, CachedQuote.class);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not deserialize cached quote", ex);
		}
	}

	private String normalizeSymbol(String symbol) {
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Symbol is required");
		}
		return symbol.trim().toUpperCase(Locale.ROOT);
	}
}
