package com.ledgerstream.portfolio;

import java.util.List;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.portfolio.dto.LedgerPageResponse;
import com.ledgerstream.portfolio.dto.PortfolioHistoryResponse;
import com.ledgerstream.portfolio.dto.PortfolioPositionResponse;
import com.ledgerstream.portfolio.dto.PortfolioSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

	private final PortfolioQueryService portfolioQueryService;
	private final PortfolioSnapshotService portfolioSnapshotService;

	public PortfolioController(
		PortfolioQueryService portfolioQueryService,
		PortfolioSnapshotService portfolioSnapshotService
	) {
		this.portfolioQueryService = portfolioQueryService;
		this.portfolioSnapshotService = portfolioSnapshotService;
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
}
