package com.ledgerstream.quotes;

import java.util.List;

import com.ledgerstream.quotes.dto.QuoteHistoryResponse;
import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.quotes.dto.SymbolResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/symbols")
public class QuoteController {

	private final QuoteQueryService quoteQueryService;

	public QuoteController(QuoteQueryService quoteQueryService) {
		this.quoteQueryService = quoteQueryService;
	}

	@GetMapping
	public List<SymbolResponse> listSymbols() {
		return quoteQueryService.listSymbols();
	}

	@GetMapping("/{ticker}")
	public SymbolResponse getSymbol(@PathVariable String ticker) {
		return quoteQueryService.getSymbol(ticker);
	}

	@GetMapping("/{ticker}/quote")
	public QuoteResponse getLatestQuote(@PathVariable String ticker) {
		return quoteQueryService.getLatestQuote(ticker);
	}

	@GetMapping("/{ticker}/history")
	public QuoteHistoryResponse getHistory(
		@PathVariable String ticker,
		@RequestParam(defaultValue = "1d") String range,
		@RequestParam(defaultValue = "500") @Min(1) @Max(500) int limit
	) {
		return quoteQueryService.getHistory(ticker, range, limit);
	}
}
