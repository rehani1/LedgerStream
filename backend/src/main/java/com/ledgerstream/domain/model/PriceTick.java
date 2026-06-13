package com.ledgerstream.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "price_ticks",
	indexes = @Index(name = "idx_price_ticks_symbol_ts_desc", columnList = "symbol_id,ts DESC"),
	uniqueConstraints = @UniqueConstraint(name = "uq_price_ticks_symbol_ts_source", columnNames = {"symbol_id", "ts", "source"})
)
public class PriceTick {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "symbol_id", nullable = false)
	private Symbol symbol;

	@Column(nullable = false)
	private Instant ts;

	@Column(precision = 18, scale = 6)
	private BigDecimal bid;

	@Column(precision = 18, scale = 6)
	private BigDecimal ask;

	@Column(name = "last", nullable = false, precision = 18, scale = 6)
	private BigDecimal lastPrice;

	private Long volume;

	@Column(nullable = false)
	private String source;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Symbol getSymbol() {
		return symbol;
	}

	public void setSymbol(Symbol symbol) {
		this.symbol = symbol;
	}

	public Instant getTs() {
		return ts;
	}

	public void setTs(Instant ts) {
		this.ts = ts;
	}

	public BigDecimal getBid() {
		return bid;
	}

	public void setBid(BigDecimal bid) {
		this.bid = bid;
	}

	public BigDecimal getAsk() {
		return ask;
	}

	public void setAsk(BigDecimal ask) {
		this.ask = ask;
	}

	public BigDecimal getLastPrice() {
		return lastPrice;
	}

	public void setLastPrice(BigDecimal lastPrice) {
		this.lastPrice = lastPrice;
	}

	public Long getVolume() {
		return volume;
	}

	public void setVolume(Long volume) {
		this.volume = volume;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}
}
