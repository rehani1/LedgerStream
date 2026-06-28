package com.ledgerstream.risk;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.risk.dto.RiskHistoryResponse;
import com.ledgerstream.risk.dto.RiskSnapshotResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/risk")
public class RiskController {

	private final RiskQueryService riskQueryService;

	public RiskController(RiskQueryService riskQueryService) {
		this.riskQueryService = riskQueryService;
	}

	@GetMapping
	public RiskSnapshotResponse getLatestRisk(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		return riskQueryService.getLatestRisk(authenticatedUser);
	}

	@GetMapping("/history")
	public RiskHistoryResponse listRiskHistory(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "50") int size
	) {
		return riskQueryService.listRiskHistory(authenticatedUser, page, size);
	}
}
