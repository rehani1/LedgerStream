package com.ledgerstream.quotes;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.quotes.dto.QuoteStreamReadyResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class QuoteStreamService {

	private static final Logger log = LoggerFactory.getLogger(QuoteStreamService.class);
	private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

	private final QuoteQueryService quoteQueryService;
	private final Clock clock;
	private final Map<String, Set<SseEmitter>> emittersBySymbol = new ConcurrentHashMap<>();
	private final Map<SseEmitter, Set<String>> subscriptionsByEmitter = new ConcurrentHashMap<>();
	private final Counter broadcastCounter;
	private final Counter failedSendCounter;

	public QuoteStreamService(QuoteQueryService quoteQueryService, Clock clock, MeterRegistry meterRegistry) {
		this.quoteQueryService = quoteQueryService;
		this.clock = clock;
		this.broadcastCounter = Counter.builder("ledgerstream_quote_stream_events_total")
			.description("Quote SSE events sent by the backend")
			.register(meterRegistry);
		this.failedSendCounter = Counter.builder("ledgerstream_quote_stream_send_failures_total")
			.description("Quote SSE events that could not be sent to a client")
			.register(meterRegistry);
		Gauge.builder("ledgerstream_quote_stream_clients", subscriptionsByEmitter, Map::size)
			.description("Active quote SSE clients")
			.register(meterRegistry);
	}

	public SseEmitter openStream(String symbols) {
		List<String> normalizedSymbols = TickerNormalizer.normalizeTickerList(symbols);
		normalizedSymbols.forEach(quoteQueryService::getSymbol);

		SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
		Set<String> subscriptions = Set.copyOf(normalizedSymbols);
		subscriptionsByEmitter.put(emitter, subscriptions);
		subscriptions.forEach(symbol -> emittersBySymbol
			.computeIfAbsent(symbol, ignored -> new CopyOnWriteArraySet<>())
			.add(emitter));

		emitter.onCompletion(() -> closeEmitter(emitter));
		emitter.onTimeout(() -> closeEmitter(emitter));
		emitter.onError(ex -> closeEmitter(emitter));

		sendReady(emitter, normalizedSymbols);
		normalizedSymbols.forEach(symbol -> sendInitialQuote(emitter, symbol));
		return emitter;
	}

	public void broadcast(QuoteResponse quote) {
		String symbol = TickerNormalizer.normalizeTicker(quote.symbol());
		Set<SseEmitter> emitters = emittersBySymbol.getOrDefault(symbol, Collections.emptySet());
		for (SseEmitter emitter : emitters) {
			if (sendEvent(emitter, "quote", eventId(quote), quote)) {
				broadcastCounter.increment();
			}
		}
	}

	int activeClientCount() {
		return subscriptionsByEmitter.size();
	}

	int subscriberCount(String symbol) {
		return emittersBySymbol.getOrDefault(TickerNormalizer.normalizeTicker(symbol), Collections.emptySet()).size();
	}

	private void sendReady(SseEmitter emitter, List<String> symbols) {
		QuoteStreamReadyResponse ready = new QuoteStreamReadyResponse(symbols, Instant.now(clock));
		sendEvent(emitter, "ready", null, ready);
	}

	private void sendInitialQuote(SseEmitter emitter, String symbol) {
		try {
			QuoteResponse quote = quoteQueryService.getLatestQuote(symbol);
			sendEvent(emitter, "quote", eventId(quote), quote);
		} catch (ResponseStatusException ex) {
			if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
				throw ex;
			}
		}
	}

	private boolean sendEvent(SseEmitter emitter, String name, String id, Object data) {
		try {
			SseEmitter.SseEventBuilder event = SseEmitter.event().name(name).data(data);
			if (id != null) {
				event.id(id);
			}
			emitter.send(event);
			return true;
		} catch (IOException | IllegalStateException ex) {
			failedSendCounter.increment();
			closeEmitter(emitter);
			log.debug("Closed quote stream after send failure", ex);
			return false;
		}
	}

	private void closeEmitter(SseEmitter emitter) {
		Set<String> subscriptions = subscriptionsByEmitter.remove(emitter);
		if (subscriptions == null) {
			return;
		}
		for (String symbol : subscriptions) {
			Set<SseEmitter> emitters = emittersBySymbol.get(symbol);
			if (emitters != null) {
				emitters.remove(emitter);
				if (emitters.isEmpty()) {
					emittersBySymbol.remove(symbol, emitters);
				}
			}
		}
	}

	private String eventId(QuoteResponse quote) {
		return quote.symbol() + ":" + quote.timestamp();
	}
}
