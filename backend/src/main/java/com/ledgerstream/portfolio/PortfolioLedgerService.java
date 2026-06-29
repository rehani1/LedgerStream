package com.ledgerstream.portfolio;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ledgerstream.domain.model.CashTransfer;
import com.ledgerstream.domain.model.Fill;
import com.ledgerstream.domain.model.LedgerEntry;
import com.ledgerstream.domain.model.LedgerEntryType;
import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.TradeOrder;
import com.ledgerstream.domain.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioLedgerService {

	private final LedgerEntryRepository ledgerEntryRepository;

	public PortfolioLedgerService(LedgerEntryRepository ledgerEntryRepository) {
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public LedgerEntry appendFill(Portfolio portfolio, Fill fill, BigDecimal cashDelta, BigDecimal quantityDelta) {
		TradeOrder order = fill.getOrder();
		LedgerEntry entry = new LedgerEntry();
		entry.setUser(order.getUser());
		entry.setPortfolio(portfolio);
		entry.setOrder(order);
		entry.setFill(fill);
		entry.setEntryType(entryType(order.getSide()));
		entry.setCashDelta(cashDelta);
		entry.setSymbol(fill.getSymbol());
		entry.setQuantityDelta(quantityDelta);
		entry.setPrice(fill.getPrice());
		entry.setMetadata(metadata(fill));
		return ledgerEntryRepository.save(entry);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public LedgerEntry appendCashTransfer(
		Portfolio portfolio,
		CashTransfer transfer,
		LedgerEntryType entryType,
		BigDecimal cashDelta
	) {
		LedgerEntry entry = new LedgerEntry();
		entry.setUser(portfolio.getUser());
		entry.setPortfolio(portfolio);
		entry.setEntryType(entryType);
		entry.setCashDelta(cashDelta);
		entry.setQuantityDelta(BigDecimal.ZERO);
		entry.setMetadata(metadata(transfer));
		return ledgerEntryRepository.save(entry);
	}

	private LedgerEntryType entryType(OrderSide side) {
		return side == OrderSide.BUY ? LedgerEntryType.BUY_FILL : LedgerEntryType.SELL_FILL;
	}

	private Map<String, Object> metadata(Fill fill) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("orderSide", fill.getOrder().getSide().name());
		metadata.put("orderType", fill.getOrder().getOrderType().name());
		metadata.put("fee", fill.getFee());
		return metadata;
	}

	private Map<String, Object> metadata(CashTransfer transfer) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("transferId", transfer.getId().toString());
		metadata.put("transferType", transfer.getTransferType().name());
		metadata.put("amount", transfer.getAmount());
		if (transfer.getNote() != null && !transfer.getNote().isBlank()) {
			metadata.put("note", transfer.getNote());
		}
		return metadata;
	}
}
