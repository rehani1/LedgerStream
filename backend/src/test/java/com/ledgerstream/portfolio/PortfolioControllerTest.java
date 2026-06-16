package com.ledgerstream.portfolio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.LedgerEntryType;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.portfolio.dto.LedgerEntryResponse;
import com.ledgerstream.portfolio.dto.LedgerPageResponse;
import com.ledgerstream.portfolio.dto.PortfolioPositionResponse;
import com.ledgerstream.portfolio.dto.PortfolioSummaryResponse;
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
class PortfolioControllerTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PortfolioQueryService portfolioQueryService;

	@Test
	void portfolioSummaryRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/portfolio"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getPortfolioReturnsSummary() throws Exception {
		PortfolioSummaryResponse response = new PortfolioSummaryResponse(
			UUID.randomUUID(),
			"USD",
			new BigDecimal("1000.00"),
			new BigDecimal("600.00"),
			new BigDecimal("1600.00"),
			new BigDecimal("12.34"),
			new BigDecimal("100.00"),
			1,
			1,
			NOW
		);
		when(portfolioQueryService.getPortfolio(any(AuthenticatedUser.class))).thenReturn(response);

		mockMvc.perform(get("/api/portfolio").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.portfolioId").value(response.portfolioId().toString()))
			.andExpect(jsonPath("$.baseCurrency").value("USD"))
			.andExpect(jsonPath("$.cash").value(1000.00))
			.andExpect(jsonPath("$.marketValue").value(600.00))
			.andExpect(jsonPath("$.totalEquity").value(1600.00))
			.andExpect(jsonPath("$.realizedPnl").value(12.34))
			.andExpect(jsonPath("$.unrealizedPnl").value(100.00))
			.andExpect(jsonPath("$.positionsCount").value(1))
			.andExpect(jsonPath("$.pricedPositionsCount").value(1));
	}

	@Test
	void listPositionsReturnsValuations() throws Exception {
		when(portfolioQueryService.listPositions(any(AuthenticatedUser.class))).thenReturn(List.of(positionResponse()));

		mockMvc.perform(get("/api/portfolio/positions").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].symbol").value("AAPL"))
			.andExpect(jsonPath("$[0].quantity").value(5.000000))
			.andExpect(jsonPath("$[0].lastPrice").value(120.000000))
			.andExpect(jsonPath("$[0].valuationSource").value("LATEST_QUOTE"))
			.andExpect(jsonPath("$[0].marketValue").value(600.00))
			.andExpect(jsonPath("$[0].unrealizedPnl").value(100.00));
	}

	@Test
	void listLedgerReturnsPagedEntries() throws Exception {
		LedgerPageResponse response = new LedgerPageResponse(List.of(ledgerEntryResponse()), 0, 25, 1, 1);
		when(portfolioQueryService.listLedger(any(AuthenticatedUser.class), eq(0), eq(25))).thenReturn(response);

		mockMvc.perform(get("/api/portfolio/ledger")
				.param("page", "0")
				.param("size", "25")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(25))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.entries[0].entryType").value("BUY_FILL"))
			.andExpect(jsonPath("$.entries[0].cashDelta").value(-1874.80))
			.andExpect(jsonPath("$.entries[0].symbol").value("AAPL"))
			.andExpect(jsonPath("$.entries[0].metadata.orderSide").value("BUY"));
	}

	private PortfolioPositionResponse positionResponse() {
		return new PortfolioPositionResponse(
			UUID.randomUUID(),
			"AAPL",
			new BigDecimal("5.000000"),
			new BigDecimal("100.000000"),
			new BigDecimal("120.000000"),
			new BigDecimal("120.000000"),
			"LATEST_QUOTE",
			new BigDecimal("600.00"),
			new BigDecimal("500.00"),
			new BigDecimal("100.00"),
			new BigDecimal("12.34"),
			NOW
		);
	}

	private LedgerEntryResponse ledgerEntryResponse() {
		return new LedgerEntryResponse(
			UUID.randomUUID(),
			LedgerEntryType.BUY_FILL,
			new BigDecimal("-1874.80"),
			"AAPL",
			new BigDecimal("10.000000"),
			new BigDecimal("187.480000"),
			UUID.randomUUID(),
			UUID.randomUUID(),
			NOW,
			Map.of("orderSide", "BUY", "orderType", "MARKET", "fee", new BigDecimal("0.00"))
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
