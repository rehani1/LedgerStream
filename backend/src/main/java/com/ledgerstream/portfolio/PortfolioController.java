package com.ledgerstream.portfolio;

import java.util.List;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.portfolio.dto.CashTransferRequest;
import com.ledgerstream.portfolio.dto.CashTransferResponse;
import com.ledgerstream.portfolio.dto.LedgerPageResponse;
import com.ledgerstream.portfolio.dto.PortfolioHistoryResponse;
import com.ledgerstream.portfolio.dto.PortfolioPositionResponse;
import com.ledgerstream.portfolio.dto.PortfolioSummaryResponse;
import com.ledgerstream.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

	private final PortfolioQueryService portfolioQueryService;
	private final PortfolioSnapshotService portfolioSnapshotService;
	private final PortfolioCashService portfolioCashService;

	public PortfolioController(
		PortfolioQueryService portfolioQueryService,
		PortfolioSnapshotService portfolioSnapshotService,
		PortfolioCashService portfolioCashService
	) {
		this.portfolioQueryService = portfolioQueryService;
		this.portfolioSnapshotService = portfolioSnapshotService;
		this.portfolioCashService = portfolioCashService;
	}

	@GetMapping
	public PortfolioSummaryResponse getPortfolio(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		return portfolioQueryService.getPortfolio(authenticatedUser);
	}

	@GetMapping("/positions")
	public List<PortfolioPositionResponse> listPositions(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		return portfolioQueryService.listPositions(authenticatedUser);
	}

	@GetMapping("/ledger")
	public LedgerPageResponse listLedger(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "50") int size
	) {
		return portfolioQueryService.listLedger(authenticatedUser, page, size);
	}

	@GetMapping("/history")
	public PortfolioHistoryResponse listHistory(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "50") int size
	) {
		return portfolioSnapshotService.listHistory(authenticatedUser, page, size);
	}

	@PostMapping("/cash/deposit")
	public ResponseEntity<CashTransferResponse> deposit(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody CashTransferRequest request,
		HttpServletRequest servletRequest
	) {
		return cashTransferResponse(
			portfolioCashService.deposit(authenticatedUser, idempotencyKey, request, requestId(servletRequest))
		);
	}

	@PostMapping("/cash/withdraw")
	public ResponseEntity<CashTransferResponse> withdraw(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody CashTransferRequest request,
		HttpServletRequest servletRequest
	) {
		return cashTransferResponse(
			portfolioCashService.withdraw(authenticatedUser, idempotencyKey, request, requestId(servletRequest))
		);
	}

	private String requestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return requestId == null ? null : requestId.toString();
	}

	private ResponseEntity<CashTransferResponse> cashTransferResponse(CashTransferResponse response) {
		HttpStatus status = response.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(response);
	}
}
