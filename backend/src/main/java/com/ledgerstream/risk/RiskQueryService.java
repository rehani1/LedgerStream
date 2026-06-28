package com.ledgerstream.risk;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.RiskSnapshot;
import com.ledgerstream.domain.repository.RiskSnapshotRepository;
import com.ledgerstream.risk.dto.RiskHistoryResponse;
import com.ledgerstream.risk.dto.RiskSnapshotResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RiskQueryService {

	private static final int MAX_HISTORY_PAGE_SIZE = 100;

	private final RiskSnapshotRepository riskSnapshotRepository;

	public RiskQueryService(RiskSnapshotRepository riskSnapshotRepository) {
		this.riskSnapshotRepository = riskSnapshotRepository;
	}

	@Transactional(readOnly = true)
	public RiskSnapshotResponse getLatestRisk(AuthenticatedUser authenticatedUser) {
		return riskSnapshotRepository.findFirstByUserIdOrderByCreatedAtDesc(authenticatedUser.id())
			.map(RiskSnapshotResponse::from)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk snapshot not found"));
	}

	@Transactional(readOnly = true)
	public RiskHistoryResponse listRiskHistory(AuthenticatedUser authenticatedUser, int page, int size) {
		int normalizedPage = normalizePage(page);
		int normalizedSize = normalizeSize(size);
		Page<RiskSnapshot> snapshotPage = riskSnapshotRepository.findByUserIdOrderByCreatedAtDesc(
			authenticatedUser.id(),
			PageRequest.of(normalizedPage, normalizedSize)
		);
		return new RiskHistoryResponse(
			snapshotPage.getContent().stream().map(RiskSnapshotResponse::from).toList(),
			normalizedPage,
			normalizedSize,
			snapshotPage.getTotalElements(),
			snapshotPage.getTotalPages()
		);
	}

	private int normalizePage(int page) {
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater");
		}
		return page;
	}

	private int normalizeSize(int size) {
		if (size < 1 || size > MAX_HISTORY_PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and 100");
		}
		return size;
	}
}
