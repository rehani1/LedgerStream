package com.ledgerstream.quotes.dto;

import java.util.UUID;

import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.Symbol;

public record SymbolResponse(
	UUID id,
	String ticker,
	String name,
	String exchange,
	AssetType assetType,
	String currency,
	boolean active
) {

	public static SymbolResponse from(Symbol symbol) {
		return new SymbolResponse(
			symbol.getId(),
			symbol.getTicker(),
			symbol.getName(),
			symbol.getExchange(),
			symbol.getAssetType(),
			symbol.getCurrency(),
			symbol.isActive()
		);
	}
}
