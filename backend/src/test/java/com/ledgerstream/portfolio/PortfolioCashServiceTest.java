package com.ledgerstream.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.CashTransfer;
import com.ledgerstream.domain.model.CashTransferStatus;
import com.ledgerstream.domain.model.CashTransferType;
import com.ledgerstream.domain.model.LedgerEntry;
import com.ledgerstream.domain.model.LedgerEntryType;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.CashTransferRepository;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.portfolio.dto.CashTransferRequest;
import com.ledgerstream.portfolio.dto.CashTransferResponse;
import com.ledgerstream.portfolio.dto.PortfolioSummaryResponse;
import com.ledgerstream.risk.RiskCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PortfolioCashServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private CashTransferRepository cashTransferRepository;

	@Mock
	private PortfolioLedgerService portfolioLedgerService;

	@Mock
	private PortfolioQueryService portfolioQueryService;

	@Mock
	private PortfolioSnapshotService portfolioSnapshotService;

	@Mock
	private RiskCalculationService riskCalculationService;

	@Mock
	private AuditService auditService;

	private PortfolioCashService cashService;
	private AuthenticatedUser authenticatedUser;
	private User user;
	private Portfolio portfolio;

	@BeforeEach
	void setUp() {
		cashService = new PortfolioCashService(
			portfolioRepository,
			cashTransferRepository,
			portfolioLedgerService,
			portfolioQueryService,
			portfolioSnapshotService,
			riskCalculationService,
			auditService
		);
		authenticatedUser = new AuthenticatedUser(USER_ID, "user@example.com", UserRole.USER);
		user = user();
		portfolio = portfolio(new BigDecimal("1000.00"));
	}

	@Test
	void depositCreatesIdempotentLedgerBackedCashTransfer() {
		when(cashTransferRepository.findByUserIdAndIdempotencyKey(USER_ID, "cash-key-1")).thenReturn(Optional.empty());
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));
		when(portfolioRepository.saveAndFlush(portfolio)).thenReturn(portfolio);
		when(cashTransferRepository.save(any(CashTransfer.class))).thenAnswer(invocation -> {
			CashTransfer transfer = invocation.getArgument(0);
			if (transfer.getId() == null) {
				transfer.setId(UUID.randomUUID());
			}
			return transfer;
		});
		when(portfolioLedgerService.appendCashTransfer(
			eq(portfolio),
			any(CashTransfer.class),
			eq(LedgerEntryType.CASH_DEPOSIT),
			eq(new BigDecimal("2500.00"))
		)).thenReturn(ledgerEntry(LedgerEntryType.CASH_DEPOSIT, new BigDecimal("2500.00")));
		when(portfolioQueryService.getPortfolio(authenticatedUser)).thenReturn(summary(new BigDecimal("3500.00")));

		CashTransferResponse response = cashService.deposit(
			authenticatedUser,
			"cash-key-1",
			new CashTransferRequest(new BigDecimal("2500.00"), "demo deposit"),
			"request-1"
		);

		assertThat(portfolio.getCashBalance()).isEqualByComparingTo("3500.00");
		assertThat(response.created()).isTrue();
		assertThat(response.transferType()).isEqualTo(CashTransferType.DEPOSIT);
		assertThat(response.amount()).isEqualByComparingTo("2500.00");
		assertThat(response.ledgerEntry().entryType()).isEqualTo(LedgerEntryType.CASH_DEPOSIT);
		assertThat(response.portfolio().cash()).isEqualByComparingTo("3500.00");
		verify(auditService).record(eq(user), eq("CASH_DEPOSITED"), eq("request-1"), any());
		verify(riskCalculationService).recordSnapshot(USER_ID);
		verify(portfolioSnapshotService).recordSnapshot(USER_ID);

		ArgumentCaptor<CashTransfer> transferCaptor = ArgumentCaptor.forClass(CashTransfer.class);
		verify(cashTransferRepository, times(2)).save(transferCaptor.capture());
		CashTransfer savedTransfer = transferCaptor.getAllValues().getFirst();
		assertThat(savedTransfer.getTransferType()).isEqualTo(CashTransferType.DEPOSIT);
		assertThat(savedTransfer.getStatus()).isEqualTo(CashTransferStatus.COMPLETED);
		assertThat(savedTransfer.getAmount()).isEqualByComparingTo("2500.00");
		assertThat(savedTransfer.getNote()).isEqualTo("demo deposit");
	}

	@Test
	void withdrawRejectsInsufficientCash() {
		when(cashTransferRepository.findByUserIdAndIdempotencyKey(USER_ID, "cash-key-2")).thenReturn(Optional.empty());
		when(portfolioRepository.findByUserId(USER_ID)).thenReturn(Optional.of(portfolio));

		assertThatThrownBy(() -> cashService.withdraw(
			authenticatedUser,
			"cash-key-2",
			new CashTransferRequest(new BigDecimal("1500.00"), null),
			"request-2"
		))
			.isInstanceOf(ResponseStatusException.class)
			.extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
			.isEqualTo(HttpStatus.CONFLICT);

		assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1000.00");
		verify(portfolioRepository, never()).saveAndFlush(any(Portfolio.class));
		verify(portfolioLedgerService, never()).appendCashTransfer(any(), any(), any(), any());
		verify(riskCalculationService, never()).recordSnapshot(any());
	}

	@Test
	void duplicateIdempotencyKeyReturnsExistingTransferWithoutChangingCash() {
		CashTransfer existingTransfer = cashTransfer(CashTransferType.WITHDRAWAL, new BigDecimal("500.00"));
		LedgerEntry existingLedgerEntry = ledgerEntry(LedgerEntryType.CASH_WITHDRAWAL, new BigDecimal("-500.00"));
		existingTransfer.setLedgerEntry(existingLedgerEntry);
		when(cashTransferRepository.findByUserIdAndIdempotencyKey(USER_ID, "cash-key-3"))
			.thenReturn(Optional.of(existingTransfer));
		when(portfolioQueryService.getPortfolio(authenticatedUser)).thenReturn(summary(new BigDecimal("500.00")));

		CashTransferResponse response = cashService.withdraw(
			authenticatedUser,
			"cash-key-3",
			new CashTransferRequest(new BigDecimal("500.00"), null),
			"request-3"
		);

		assertThat(response.created()).isFalse();
		assertThat(response.transferId()).isEqualTo(existingTransfer.getId());
		assertThat(response.ledgerEntry().entryType()).isEqualTo(LedgerEntryType.CASH_WITHDRAWAL);
		verify(portfolioRepository, never()).saveAndFlush(any(Portfolio.class));
		verify(portfolioLedgerService, never()).appendCashTransfer(any(), any(), any(), any());
	}

	@Test
	void duplicateIdempotencyKeyWithDifferentAmountIsRejected() {
		CashTransfer existingTransfer = cashTransfer(CashTransferType.DEPOSIT, new BigDecimal("100.00"));
		existingTransfer.setLedgerEntry(ledgerEntry(LedgerEntryType.CASH_DEPOSIT, new BigDecimal("100.00")));
		when(cashTransferRepository.findByUserIdAndIdempotencyKey(USER_ID, "cash-key-4"))
			.thenReturn(Optional.of(existingTransfer));

		assertThatThrownBy(() -> cashService.deposit(
			authenticatedUser,
			"cash-key-4",
			new CashTransferRequest(new BigDecimal("101.00"), null),
			"request-4"
		))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Idempotency key already used");
	}

	@Test
	void transferRejectsMoreThanTwoDecimalPlaces() {
		assertThatThrownBy(() -> cashService.deposit(
			authenticatedUser,
			"cash-key-5",
			new CashTransferRequest(new BigDecimal("10.001"), null),
			"request-5"
		))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Amount can include at most two decimal places");
	}

	private CashTransfer cashTransfer(CashTransferType transferType, BigDecimal amount) {
		CashTransfer transfer = new CashTransfer();
		transfer.setId(UUID.randomUUID());
		transfer.setUser(user);
		transfer.setPortfolio(portfolio);
		transfer.setTransferType(transferType);
		transfer.setAmount(amount);
		transfer.setStatus(CashTransferStatus.COMPLETED);
		transfer.setIdempotencyKey("cash-key");
		transfer.setCreatedAt(NOW);
		return transfer;
	}

	private LedgerEntry ledgerEntry(LedgerEntryType entryType, BigDecimal cashDelta) {
		LedgerEntry entry = new LedgerEntry();
		entry.setId(UUID.randomUUID());
		entry.setUser(user);
		entry.setPortfolio(portfolio);
		entry.setEntryType(entryType);
		entry.setCashDelta(cashDelta);
		entry.setQuantityDelta(new BigDecimal("0.000000"));
		entry.setCreatedAt(NOW);
		return entry;
	}

	private PortfolioSummaryResponse summary(BigDecimal cash) {
		return new PortfolioSummaryResponse(
			portfolio.getId(),
			"USD",
			cash,
			new BigDecimal("0.00"),
			cash,
			new BigDecimal("0.00"),
			new BigDecimal("0.00"),
			0,
			0,
			NOW
		);
	}

	private Portfolio portfolio(BigDecimal cash) {
		Portfolio testPortfolio = new Portfolio();
		testPortfolio.setId(UUID.randomUUID());
		testPortfolio.setUser(user);
		testPortfolio.setCashBalance(cash);
		testPortfolio.setBaseCurrency("USD");
		testPortfolio.setCreatedAt(NOW);
		testPortfolio.setUpdatedAt(NOW);
		return testPortfolio;
	}

	private User user() {
		User testUser = new User();
		testUser.setId(USER_ID);
		testUser.setEmail("user@example.com");
		testUser.setRole(UserRole.USER);
		testUser.setPasswordHash("hash");
		return testUser;
	}
}
