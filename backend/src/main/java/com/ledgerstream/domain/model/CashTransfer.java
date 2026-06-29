package com.ledgerstream.domain.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "cash_transfers",
	indexes = @Index(name = "idx_cash_transfers_user_created_desc", columnList = "user_id,created_at DESC"),
	uniqueConstraints = @UniqueConstraint(
		name = "uq_cash_transfers_user_idempotency_key",
		columnNames = {"user_id", "idempotency_key"}
	)
)
public class CashTransfer extends CreatedAtUuidEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ledger_entry_id", unique = true)
	private LedgerEntry ledgerEntry;

	@Enumerated(EnumType.STRING)
	@Column(name = "transfer_type", nullable = false)
	private CashTransferType transferType;

	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CashTransferStatus status;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	@Column
	private String note;

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public Portfolio getPortfolio() {
		return portfolio;
	}

	public void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
	}

	public LedgerEntry getLedgerEntry() {
		return ledgerEntry;
	}

	public void setLedgerEntry(LedgerEntry ledgerEntry) {
		this.ledgerEntry = ledgerEntry;
	}

	public CashTransferType getTransferType() {
		return transferType;
	}

	public void setTransferType(CashTransferType transferType) {
		this.transferType = transferType;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public CashTransferStatus getStatus() {
		return status;
	}

	public void setStatus(CashTransferStatus status) {
		this.status = status;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
}
