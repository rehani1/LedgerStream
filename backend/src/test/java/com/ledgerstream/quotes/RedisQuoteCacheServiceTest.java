package com.ledgerstream.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ledgerstream.config.properties.RedisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisQuoteCacheServiceTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private RedisQuoteCacheService quoteCacheService;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = JsonMapper.builder()
			.addModule(new JavaTimeModule())
			.build();
		RedisProperties redisProperties = new RedisProperties("localhost", 6379, "latest_quote");
		quoteCacheService = new RedisQuoteCacheService(redisTemplate, objectMapper, redisProperties);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	@Test
	void putLatestQuoteStoresNormalizedKeyAndJsonPayload() {
		CachedQuote quote = quote("aapl");
		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

		quoteCacheService.putLatestQuote(quote);

		verify(valueOperations).set(eq("latest_quote:AAPL"), payloadCaptor.capture());
		assertThat(payloadCaptor.getValue()).contains("\"symbol\":\"AAPL\"");
		assertThat(payloadCaptor.getValue()).contains("\"source\":\"fixture\"");
	}

	@Test
	void getLatestQuoteReturnsParsedCachedQuote() throws Exception {
		CachedQuote quote = quote("MSFT");
		ObjectMapper objectMapper = JsonMapper.builder()
			.addModule(new JavaTimeModule())
			.build();
		when(valueOperations.get("latest_quote:MSFT")).thenReturn(objectMapper.writeValueAsString(quote));

		Optional<CachedQuote> cachedQuote = quoteCacheService.getLatestQuote(" msft ");

		assertThat(cachedQuote).contains(quote);
	}

	@Test
	void getLatestQuoteReturnsEmptyWhenKeyIsMissing() {
		when(valueOperations.get("latest_quote:NVDA")).thenReturn(null);

		assertThat(quoteCacheService.getLatestQuote("NVDA")).isEmpty();
	}

	private CachedQuote quote(String symbol) {
		return new CachedQuote(
			symbol,
			Instant.parse("2026-01-01T14:30:00Z"),
			new BigDecimal("187.120000"),
			new BigDecimal("187.180000"),
			new BigDecimal("187.150000"),
			1_000L,
			"fixture"
		);
	}
}
