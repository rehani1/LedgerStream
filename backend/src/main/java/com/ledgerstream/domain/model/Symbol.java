package com.ledgerstream.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
	name = "symbols",
	indexes = @Index(name = "idx_symbols_active_ticker", columnList = "active,ticker")
)
public class Symbol extends CreatedAtUuidEntity {

	@Column(nullable = false, unique = true)
	private String ticker;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String exchange;

	@Enumerated(EnumType.STRING)
	@Column(name = "asset_type", nullable = false)
	private AssetType assetType;

	@Column(nullable = false)
	private String currency = "USD";

	@Column(nullable = false)
	private boolean active = true;

	public String getTicker() {
		return ticker;
	}

	public void setTicker(String ticker) {
		this.ticker = ticker;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getExchange() {
		return exchange;
	}

	public void setExchange(String exchange) {
		this.exchange = exchange;
	}

	public AssetType getAssetType() {
		return assetType;
	}

	public void setAssetType(AssetType assetType) {
		this.assetType = assetType;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
}
