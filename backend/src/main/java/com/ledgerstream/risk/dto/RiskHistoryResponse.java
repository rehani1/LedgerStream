package com.ledgerstream.risk.dto;

import java.util.List;

public record RiskHistoryResponse(
	List<RiskSnapshotResponse> snapshots,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}
