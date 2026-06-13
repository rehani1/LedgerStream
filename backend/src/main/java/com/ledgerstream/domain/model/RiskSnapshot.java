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
	name = "risk_snapshots",
	indexes = @Index(name = "idx_risk_snapshots_user_created_desc", columnList = "user_id,created_at DESC")
)
public class RiskSnapshot extends CreatedAtUuidEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "total_equity", nullable = false, precision = 18, scale = 2)
	private BigDecimal totalEquity;

	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal cash;

	@Column(name = "gross_exposure", nullable = false, precision = 18, scale = 2)
	private BigDecimal grossExposure;

	@Column(name = "largest_position_pct", nullable = false, precision = 8, scale = 4)
	private BigDecimal largestPositionPct;

	@Column(name = "unrealized_pnl", nullable = false, precision = 18, scale = 2)
	private BigDecimal unrealizedPnl;

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
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

	public BigDecimal getGrossExposure() {
		return grossExposure;
	}

	public void setGrossExposure(BigDecimal grossExposure) {
		this.grossExposure = grossExposure;
	}

	public BigDecimal getLargestPositionPct() {
		return largestPositionPct;
	}

	public void setLargestPositionPct(BigDecimal largestPositionPct) {
		this.largestPositionPct = largestPositionPct;
	}

	public BigDecimal getUnrealizedPnl() {
		return unrealizedPnl;
	}

	public void setUnrealizedPnl(BigDecimal unrealizedPnl) {
		this.unrealizedPnl = unrealizedPnl;
	}
}
