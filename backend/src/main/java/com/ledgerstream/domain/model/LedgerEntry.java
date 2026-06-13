package com.ledgerstream.domain.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
	name = "ledger_entries",
	indexes = {
		@Index(name = "idx_ledger_entries_user_created_desc", columnList = "user_id,created_at DESC"),
		@Index(name = "idx_ledger_entries_order_id", columnList = "order_id")
	}
)
public class LedgerEntry extends CreatedAtUuidEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private TradeOrder order;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fill_id")
	private Fill fill;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false)
	private LedgerEntryType entryType;

	@Column(name = "cash_delta", nullable = false, precision = 18, scale = 2)
	private BigDecimal cashDelta = BigDecimal.ZERO;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "symbol_id")
	private Symbol symbol;

	@Column(name = "quantity_delta", nullable = false, precision = 18, scale = 6)
	private BigDecimal quantityDelta = BigDecimal.ZERO;

	@Column(precision = 18, scale = 6)
	private BigDecimal price;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> metadata = new LinkedHashMap<>();

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

	public TradeOrder getOrder() {
		return order;
	}

	public void setOrder(TradeOrder order) {
		this.order = order;
	}

	public Fill getFill() {
		return fill;
	}

	public void setFill(Fill fill) {
		this.fill = fill;
	}

	public LedgerEntryType getEntryType() {
		return entryType;
	}

	public void setEntryType(LedgerEntryType entryType) {
		this.entryType = entryType;
	}

	public BigDecimal getCashDelta() {
		return cashDelta;
	}

	public void setCashDelta(BigDecimal cashDelta) {
		this.cashDelta = cashDelta;
	}

	public Symbol getSymbol() {
		return symbol;
	}

	public void setSymbol(Symbol symbol) {
		this.symbol = symbol;
	}

	public BigDecimal getQuantityDelta() {
		return quantityDelta;
	}

	public void setQuantityDelta(BigDecimal quantityDelta) {
		this.quantityDelta = quantityDelta;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}
}
