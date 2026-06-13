package com.ledgerstream.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
	name = "fills",
	indexes = {
		@Index(name = "idx_fills_order_id", columnList = "order_id"),
		@Index(name = "idx_fills_symbol_filled_at", columnList = "symbol_id,filled_at DESC")
	}
)
public class Fill extends UuidEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private TradeOrder order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "symbol_id", nullable = false)
	private Symbol symbol;

	@Column(nullable = false, precision = 18, scale = 6)
	private BigDecimal price;

	@Column(nullable = false, precision = 18, scale = 6)
	private BigDecimal quantity;

	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal fee = BigDecimal.ZERO;

	@Column(name = "filled_at", nullable = false)
	private Instant filledAt;

	@PrePersist
	protected void markFilledAt() {
		if (filledAt == null) {
			filledAt = Instant.now();
		}
	}

	public TradeOrder getOrder() {
		return order;
	}

	public void setOrder(TradeOrder order) {
		this.order = order;
	}

	public Symbol getSymbol() {
		return symbol;
	}

	public void setSymbol(Symbol symbol) {
		this.symbol = symbol;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getFee() {
		return fee;
	}

	public void setFee(BigDecimal fee) {
		this.fee = fee;
	}

	public Instant getFilledAt() {
		return filledAt;
	}

	public void setFilledAt(Instant filledAt) {
		this.filledAt = filledAt;
	}
}
