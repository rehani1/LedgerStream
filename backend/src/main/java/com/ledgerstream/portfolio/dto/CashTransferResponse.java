package com.ledgerstream.portfolio.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.ledgerstream.domain.model.CashTransferType;

public record CashTransferResponse(
	UUID transferId,
	CashTransferType transferType,
	BigDecimal amount,
	boolean created,
	PortfolioSummaryResponse portfolio,
	LedgerEntryResponse ledgerEntry
) {
}
