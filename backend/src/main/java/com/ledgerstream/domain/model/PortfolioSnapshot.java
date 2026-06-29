package com.ledgerstream.domain.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
	name = "portfolio_snapshots",
	indexes = @Index(name = "idx_portfolio_snapshots_user_created_desc", columnList = "user_id,created_at DESC")
)
public class PortfolioSnapshot extends CreatedAtUuidEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	@Column(name = "total_equity", nullable = false, precision = 18, scale = 2)
	private BigDecimal totalEquity;

	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal cash;

	@Column(name = "market_value", nullable = false, precision = 18, scale = 2)
	private BigDecimal marketValue;

	@Column(name = "gross_exposure", nullable = false, precision = 18, scale = 2)
	private BigDecimal grossExposure;

	@Column(name = "realized_pnl", nullable = false, precision = 18, scale = 2)
	private BigDecimal realizedPnl;

	@Column(name = "unrealized_pnl", nullable = false, precision = 18, scale = 2)
	private BigDecimal unrealizedPnl;

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

	public BigDecimal getTotalEquity() {
		return totalEquity;
	}

	public void setTotalEquity(BigDecimal totalEquity) {
		this.totalEquity = totalEquity;
	}

	public BigDecimal getCash() {
		return cash;
	}

	public void setCash(BigDecimal cash) {
		this.cash = cash;
	}

	public BigDecimal getMarketValue() {
		return marketValue;
	}

	public void setMarketValue(BigDecimal marketValue) {
		this.marketValue = marketValue;
	}

	public BigDecimal getGrossExposure() {
		return grossExposure;
	}

	public void setGrossExposure(BigDecimal grossExposure) {
		this.grossExposure = grossExposure;
	}

	public BigDecimal getRealizedPnl() {
		return realizedPnl;
	}

	public void setRealizedPnl(BigDecimal realizedPnl) {
		this.realizedPnl = realizedPnl;
	}

	public BigDecimal getUnrealizedPnl() {
		return unrealizedPnl;
	}

	public void setUnrealizedPnl(BigDecimal unrealizedPnl) {
		this.unrealizedPnl = unrealizedPnl;
	}
}
