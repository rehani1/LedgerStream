package com.ledgerstream.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.CashTransfer;
import com.ledgerstream.domain.model.CashTransferStatus;
import com.ledgerstream.domain.model.CashTransferType;
import com.ledgerstream.domain.model.LedgerEntry;
import com.ledgerstream.domain.model.LedgerEntryType;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.repository.CashTransferRepository;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.portfolio.dto.CashTransferRequest;
import com.ledgerstream.portfolio.dto.CashTransferResponse;
import com.ledgerstream.portfolio.dto.LedgerEntryResponse;
import com.ledgerstream.risk.RiskCalculationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioCashService {

	private static final int MONEY_SCALE = 2;
	private static final int MAX_NOTE_LENGTH = 120;
	private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
	private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("1000000.00");
	private static final RoundingMode ACCOUNTING_ROUNDING = RoundingMode.HALF_UP;

	private final PortfolioRepository portfolioRepository;
	private final CashTransferRepository cashTransferRepository;
	private final PortfolioLedgerService portfolioLedgerService;
	private final PortfolioQueryService portfolioQueryService;
	private final PortfolioSnapshotService portfolioSnapshotService;
	private final RiskCalculationService riskCalculationService;
	private final AuditService auditService;

	public PortfolioCashService(
		PortfolioRepository portfolioRepository,
		CashTransferRepository cashTransferRepository,
		PortfolioLedgerService portfolioLedgerService,
		PortfolioQueryService portfolioQueryService,
		PortfolioSnapshotService portfolioSnapshotService,
		RiskCalculationService riskCalculationService,
		AuditService auditService
	) {
		this.portfolioRepository = portfolioRepository;
		this.cashTransferRepository = cashTransferRepository;
		this.portfolioLedgerService = portfolioLedgerService;
		this.portfolioQueryService = portfolioQueryService;
		this.portfolioSnapshotService = portfolioSnapshotService;
		this.riskCalculationService = riskCalculationService;
		this.auditService = auditService;
	}

	@Transactional
	public CashTransferResponse deposit(
		AuthenticatedUser authenticatedUser,
		String idempotencyKey,
		CashTransferRequest request,
		String requestId
	) {
		return transfer(authenticatedUser, idempotencyKey, request, requestId, CashTransferType.DEPOSIT);
	}

	@Transactional
	public CashTransferResponse withdraw(
		AuthenticatedUser authenticatedUser,
		String idempotencyKey,
		CashTransferRequest request,
		String requestId
	) {
		return transfer(authenticatedUser, idempotencyKey, request, requestId, CashTransferType.WITHDRAWAL);
	}

	private CashTransferResponse transfer(
		AuthenticatedUser authenticatedUser,
		String idempotencyKey,
		CashTransferRequest request,
		String requestId,
		CashTransferType transferType
	) {
		String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
		BigDecimal amount = normalizeAmount(request);
		String note = normalizeNote(request);

		return cashTransferRepository.findByUserIdAndIdempotencyKey(authenticatedUser.id(), normalizedKey)
			.map(existing -> existingResponse(authenticatedUser, existing, transferType, amount))
			.orElseGet(() -> createTransfer(authenticatedUser, normalizedKey, amount, note, requestId, transferType));
	}

	private CashTransferResponse existingResponse(
		AuthenticatedUser authenticatedUser,
		CashTransfer transfer,
		CashTransferType requestedType,
		BigDecimal requestedAmount
	) {
		if (transfer.getTransferType() != requestedType || transfer.getAmount().compareTo(requestedAmount) != 0) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT,
				"Idempotency key already used for a different cash transfer"
			);
		}
		LedgerEntry ledgerEntry = transfer.getLedgerEntry();
		if (ledgerEntry == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Cash transfer is missing its ledger entry");
		}
		return new CashTransferResponse(
			transfer.getId(),
			transfer.getTransferType(),
			transfer.getAmount(),
			false,
			portfolioQueryService.getPortfolio(authenticatedUser),
			LedgerEntryResponse.from(ledgerEntry)
		);
	}

	private CashTransferResponse createTransfer(
		AuthenticatedUser authenticatedUser,
		String idempotencyKey,
		BigDecimal amount,
		String note,
		String requestId,
		CashTransferType transferType
	) {
		Portfolio portfolio = portfolioRepository.findByUserId(authenticatedUser.id())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));

		BigDecimal cashDelta = cashDelta(transferType, amount);
		BigDecimal updatedCash = money(portfolio.getCashBalance().add(cashDelta));
		if (updatedCash.compareTo(BigDecimal.ZERO) < 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient cash");
		}

		portfolio.setCashBalance(updatedCash);
		portfolioRepository.saveAndFlush(portfolio);

		CashTransfer transfer = new CashTransfer();
		transfer.setUser(portfolio.getUser());
		transfer.setPortfolio(portfolio);
		transfer.setTransferType(transferType);
		transfer.setAmount(amount);
		transfer.setStatus(CashTransferStatus.COMPLETED);
		transfer.setIdempotencyKey(idempotencyKey);
		transfer.setNote(note);
		CashTransfer savedTransfer = cashTransferRepository.save(transfer);

		LedgerEntry ledgerEntry = portfolioLedgerService.appendCashTransfer(
			portfolio,
			savedTransfer,
			ledgerEntryType(transferType),
			cashDelta
		);
		savedTransfer.setLedgerEntry(ledgerEntry);
		cashTransferRepository.save(savedTransfer);

		auditService.record(portfolio.getUser(), auditAction(transferType), requestId, auditMetadata(savedTransfer));
		riskCalculationService.recordSnapshot(portfolio.getUser().getId());
		portfolioSnapshotService.recordSnapshot(portfolio.getUser().getId());

		return new CashTransferResponse(
			savedTransfer.getId(),
			savedTransfer.getTransferType(),
			savedTransfer.getAmount(),
			true,
			portfolioQueryService.getPortfolio(authenticatedUser),
			LedgerEntryResponse.from(ledgerEntry)
		);
	}

	private BigDecimal cashDelta(CashTransferType transferType, BigDecimal amount) {
		return transferType == CashTransferType.DEPOSIT ? amount : amount.negate();
	}

	private LedgerEntryType ledgerEntryType(CashTransferType transferType) {
		return transferType == CashTransferType.DEPOSIT ? LedgerEntryType.CASH_DEPOSIT : LedgerEntryType.CASH_WITHDRAWAL;
	}

	private String auditAction(CashTransferType transferType) {
		return transferType == CashTransferType.DEPOSIT ? "CASH_DEPOSITED" : "CASH_WITHDRAWN";
	}

	private Map<String, Object> auditMetadata(CashTransfer transfer) {
		return Map.of(
			"transferId", transfer.getId().toString(),
			"transferType", transfer.getTransferType().name(),
			"amount", transfer.getAmount()
		);
	}

	private BigDecimal normalizeAmount(CashTransferRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cash transfer request is required");
		}
		if (request.amount() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required");
		}
		if (request.amount().scale() > MONEY_SCALE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount can include at most two decimal places");
		}
		BigDecimal amount = money(request.amount());
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
		}
		if (amount.compareTo(MAX_TRANSFER_AMOUNT) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be 1000000.00 or less");
		}
		return amount;
	}

	private String normalizeNote(CashTransferRequest request) {
		if (request == null || request.note() == null) {
			return null;
		}
		String note = request.note().trim();
		if (note.isBlank()) {
			return null;
		}
		if (note.length() > MAX_NOTE_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note must be 120 characters or fewer");
		}
		return note;
	}

	private String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency key is required");
		}
		String normalizedKey = idempotencyKey.trim();
		if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalizedKey).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency key is invalid");
		}
		return normalizedKey.toLowerCase(Locale.ROOT);
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(MONEY_SCALE, ACCOUNTING_ROUNDING);
	}
}
