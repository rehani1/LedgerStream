package com.ledgerstream.portfolio.dto;

import java.math.BigDecimal;

public record CashTransferRequest(BigDecimal amount, String note) {
}
