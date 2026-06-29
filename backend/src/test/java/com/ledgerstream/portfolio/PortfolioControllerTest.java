package com.ledgerstream.portfolio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.CashTransferType;
import com.ledgerstream.domain.model.LedgerEntryType;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.portfolio.dto.CashTransferRequest;
import com.ledgerstream.portfolio.dto.CashTransferResponse;
import com.ledgerstream.portfolio.dto.LedgerEntryResponse;
import com.ledgerstream.portfolio.dto.LedgerPageResponse;
import com.ledgerstream.portfolio.dto.PortfolioHistoryResponse;
import com.ledgerstream.portfolio.dto.PortfolioPositionResponse;
import com.ledgerstream.portfolio.dto.PortfolioSnapshotResponse;
import com.ledgerstream.portfolio.dto.PortfolioSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

	@MockitoBean
	private PortfolioSnapshotService portfolioSnapshotService;

	@MockitoBean
	private PortfolioCashService portfolioCashService;

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

	@Test
	void listHistoryReturnsPagedSnapshots() throws Exception {
		PortfolioHistoryResponse response = new PortfolioHistoryResponse(List.of(snapshotResponse()), 0, 25, 1, 1);
		when(portfolioSnapshotService.listHistory(any(AuthenticatedUser.class), eq(0), eq(25))).thenReturn(response);

		mockMvc.perform(get("/api/portfolio/history")
				.param("page", "0")
				.param("size", "25")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(25))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.snapshots[0].totalEquity").value(1600.00))
			.andExpect(jsonPath("$.snapshots[0].cash").value(1000.00))
			.andExpect(jsonPath("$.snapshots[0].marketValue").value(600.00))
			.andExpect(jsonPath("$.snapshots[0].unrealizedPnl").value(100.00));
	}

	@Test
	void depositCashReturnsCreatedTransfer() throws Exception {
		CashTransferResponse response = cashTransferResponse(true, LedgerEntryType.CASH_DEPOSIT, new BigDecimal("25000.00"));
		when(portfolioCashService.deposit(
			any(AuthenticatedUser.class),
			eq("cash-key-1"),
			any(CashTransferRequest.class),
			eq("cash-request-1")
		)).thenReturn(response);

		mockMvc.perform(post("/api/portfolio/cash/deposit")
				.with(authentication(authenticatedUser()))
				.header("Idempotency-Key", "cash-key-1")
				.header("X-Request-ID", "cash-request-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cashTransferJson("25000.00")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.created").value(true))
			.andExpect(jsonPath("$.transferType").value("DEPOSIT"))
			.andExpect(jsonPath("$.amount").value(25000.00))
			.andExpect(jsonPath("$.portfolio.cash").value(25000.00))
			.andExpect(jsonPath("$.ledgerEntry.entryType").value("CASH_DEPOSIT"))
			.andExpect(jsonPath("$.ledgerEntry.cashDelta").value(25000.00));

		verify(portfolioCashService).deposit(
			any(AuthenticatedUser.class),
			eq("cash-key-1"),
			any(CashTransferRequest.class),
			eq("cash-request-1")
		);
	}

	@Test
	void duplicateWithdrawalReturnsOkTransfer() throws Exception {
		CashTransferResponse response = cashTransferResponse(false, LedgerEntryType.CASH_WITHDRAWAL, new BigDecimal("-500.00"));
		when(portfolioCashService.withdraw(
			any(AuthenticatedUser.class),
			eq("cash-key-2"),
			any(CashTransferRequest.class),
			anyString()
		)).thenReturn(response);

		mockMvc.perform(post("/api/portfolio/cash/withdraw")
				.with(authentication(authenticatedUser()))
				.header("Idempotency-Key", "cash-key-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cashTransferJson("500.00")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(false))
			.andExpect(jsonPath("$.transferType").value("WITHDRAWAL"))
			.andExpect(jsonPath("$.ledgerEntry.entryType").value("CASH_WITHDRAWAL"))
			.andExpect(jsonPath("$.ledgerEntry.cashDelta").value(-500.00));
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

	private CashTransferResponse cashTransferResponse(boolean created, LedgerEntryType entryType, BigDecimal cashDelta) {
		BigDecimal cash = entryType == LedgerEntryType.CASH_DEPOSIT ? cashDelta : new BigDecimal("24500.00");
		CashTransferType transferType = entryType == LedgerEntryType.CASH_DEPOSIT
			? CashTransferType.DEPOSIT
			: CashTransferType.WITHDRAWAL;
		return new CashTransferResponse(
			UUID.randomUUID(),
			transferType,
			cashDelta.abs(),
			created,
			new PortfolioSummaryResponse(
				UUID.randomUUID(),
				"USD",
				cash,
				new BigDecimal("0.00"),
				cash,
				new BigDecimal("0.00"),
				new BigDecimal("0.00"),
				0,
				0,
				NOW
			),
			new LedgerEntryResponse(
				UUID.randomUUID(),
				entryType,
				cashDelta,
				null,
				new BigDecimal("0.000000"),
				null,
				null,
				null,
				NOW,
				Map.of("transferType", transferType.name())
			)
		);
	}

	private String cashTransferJson(String amount) {
		return """
			{
			  "amount": %s,
			  "note": "Demo paper cash movement"
			}
			""".formatted(amount);
	}

	private PortfolioSnapshotResponse snapshotResponse() {
		return new PortfolioSnapshotResponse(
			UUID.randomUUID(),
			UUID.randomUUID(),
			new BigDecimal("1600.00"),
			new BigDecimal("1000.00"),
			new BigDecimal("600.00"),
			new BigDecimal("600.00"),
			new BigDecimal("12.34"),
			new BigDecimal("100.00"),
			NOW
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
