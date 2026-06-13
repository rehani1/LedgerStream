package com.ledgerstream.quotes;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.quotes.dto.QuoteHistoryResponse;
import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.quotes.dto.SymbolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class QuoteControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private QuoteQueryService quoteQueryService;

	@Test
	void symbolListRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/symbols"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void symbolListReturnsSupportedSymbols() throws Exception {
		when(quoteQueryService.listSymbols()).thenReturn(List.of(symbolResponse("AAPL"), symbolResponse("MSFT")));

		mockMvc.perform(get("/api/symbols").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].ticker").value("AAPL"))
			.andExpect(jsonPath("$[0].assetType").value("EQUITY"))
			.andExpect(jsonPath("$[1].ticker").value("MSFT"));
	}

	@Test
	void latestQuoteReturnsQuotePayload() throws Exception {
		when(quoteQueryService.getLatestQuote("AAPL")).thenReturn(quoteResponse("AAPL"));

		mockMvc.perform(get("/api/symbols/AAPL/quote").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.symbol").value("AAPL"))
			.andExpect(jsonPath("$.timestamp").value("2026-01-02T14:34:00Z"))
			.andExpect(jsonPath("$.last").value(187.42))
			.andExpect(jsonPath("$.source").value("fixture"));
	}

	@Test
	void historyReturnsBoundedTickPayload() throws Exception {
		when(quoteQueryService.getHistory("AAPL", "1d", 100))
			.thenReturn(new QuoteHistoryResponse("AAPL", "1d", 100, List.of(quoteResponse("AAPL"))));

		mockMvc.perform(get("/api/symbols/AAPL/history")
				.param("range", "1d")
				.param("limit", "100")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.symbol").value("AAPL"))
			.andExpect(jsonPath("$.range").value("1d"))
			.andExpect(jsonPath("$.limit").value(100))
			.andExpect(jsonPath("$.ticks[0].symbol").value("AAPL"));
	}

	@Test
	void historyRejectsLimitAboveMaximumBeforeServiceCall() throws Exception {
		mockMvc.perform(get("/api/symbols/AAPL/history")
				.param("limit", "501")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400));

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

	private UsernamePasswordAuthenticationToken authenticatedUser() {
		AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), "user@example.com", UserRole.USER);
		return new UsernamePasswordAuthenticationToken(
			principal,
			"token",
			List.of(new SimpleGrantedAuthority("ROLE_USER"))
		);
	}
}
