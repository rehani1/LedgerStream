package com.ledgerstream.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.quotes.dto.SymbolResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class QuoteStreamServiceTest {

	@Mock
	private QuoteQueryService quoteQueryService;

	private SimpleMeterRegistry meterRegistry;
	private QuoteStreamService quoteStreamService;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		quoteStreamService = new QuoteStreamService(
			quoteQueryService,
			Clock.fixed(Instant.parse("2026-01-02T14:35:00Z"), ZoneOffset.UTC),
			meterRegistry
		);
	}

	@Test
	void openStreamNormalizesDeduplicatesAndRegistersSymbols() {
		when(quoteQueryService.getSymbol("AAPL")).thenReturn(symbolResponse("AAPL"));
		when(quoteQueryService.getSymbol("MSFT")).thenReturn(symbolResponse("MSFT"));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quoteResponse("AAPL"));
		when(quoteQueryService.getLatestQuote("MSFT")).thenReturn(quoteResponse("MSFT"));

		SseEmitter emitter = quoteStreamService.openStream(" aapl,MSFT,aapl ");

		assertThat(emitter).isNotNull();
		assertThat(quoteStreamService.activeClientCount()).isEqualTo(1);
		assertThat(quoteStreamService.subscriberCount("AAPL")).isEqualTo(1);
		assertThat(quoteStreamService.subscriberCount("MSFT")).isEqualTo(1);
		verify(quoteQueryService).getSymbol("AAPL");
		verify(quoteQueryService).getSymbol("MSFT");
	}

	@Test
	void broadcastCountsEventsForSubscribedSymbol() {
		when(quoteQueryService.getSymbol("AAPL")).thenReturn(symbolResponse("AAPL"));
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quoteResponse("AAPL"));
		quoteStreamService.openStream("AAPL");

		quoteStreamService.broadcast(quoteResponse("AAPL"));

		assertThat(meterRegistry.get("ledgerstream_quote_stream_events_total").counter().count()).isEqualTo(1.0);
	}

	@Test
	void openStreamRejectsMissingSymbolsParameter() {
		assertThatThrownBy(() -> quoteStreamService.openStream(" "))
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode().value()).isEqualTo(400);
				assertThat(ex.getReason()).isEqualTo("At least one symbol is required");
			});

		verifyNoInteractions(quoteQueryService);
	}

	private SymbolResponse symbolResponse(String ticker) {
		return new SymbolResponse(
			UUID.randomUUID(),
			ticker,
			ticker + " Inc.",
			"NASDAQ",
			AssetType.EQUITY,
			"USD",
			true
		);
	}

	private QuoteResponse quoteResponse(String ticker) {
		return new QuoteResponse(
			ticker,
			Instant.parse("2026-01-02T14:34:00Z"),
			new BigDecimal("187.360000"),
			new BigDecimal("187.480000"),
			new BigDecimal("187.420000"),
			136_200L,
			"fixture"
		);
	}
}
