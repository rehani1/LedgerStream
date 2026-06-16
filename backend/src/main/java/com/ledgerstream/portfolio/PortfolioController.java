package com.ledgerstream.portfolio;

import java.util.List;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.portfolio.dto.LedgerPageResponse;
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

	public PortfolioController(PortfolioQueryService portfolioQueryService) {
		this.portfolioQueryService = portfolioQueryService;
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
}
