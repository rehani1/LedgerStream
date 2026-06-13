package com.ledgerstream.quotes;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
public class QuoteStreamController {

	private final QuoteStreamService quoteStreamService;

	public QuoteStreamController(QuoteStreamService quoteStreamService) {
		this.quoteStreamService = quoteStreamService;
	}

	@GetMapping(value = "/quotes", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamQuotes(@RequestParam String symbols) {
		return quoteStreamService.openStream(symbols);
	}
}
