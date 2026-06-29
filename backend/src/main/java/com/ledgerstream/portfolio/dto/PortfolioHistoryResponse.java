package com.ledgerstream.portfolio.dto;

import java.util.List;

public record PortfolioHistoryResponse(
	List<PortfolioSnapshotResponse> snapshots,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}
