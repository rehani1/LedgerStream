package com.ledgerstream.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ledgerstream.domain.model.CashTransfer;
import com.ledgerstream.domain.model.CashTransferStatus;
import com.ledgerstream.domain.model.CashTransferType;
import com.ledgerstream.domain.model.AssetType;
import com.ledgerstream.domain.model.Fill;
import com.ledgerstream.domain.model.LedgerEntry;
import com.ledgerstream.domain.model.LedgerEntryType;
import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.Symbol;
import com.ledgerstream.domain.model.TradeOrder;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioLedgerServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

	@Mock
	private LedgerEntryRepository ledgerEntryRepository;

	private PortfolioLedgerService ledgerService;
	private User user;
	private Symbol symbol;
	private Portfolio portfolio;

	@BeforeEach
	void setUp() {
		ledgerService = new PortfolioLedgerService(ledgerEntryRepository);
		user = user();
		symbol = symbol("AAPL");
		portfolio = portfolio();
	}

	@Test
	void appendFillCreatesBuyFillLedgerEntry() {
		Fill fill = fill(order(OrderSide.BUY), new BigDecimal("187.480000"), new BigDecimal("10.000000"));
		when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LedgerEntry entry = ledgerService.appendFill(
			portfolio,
			fill,
			new BigDecimal("-1874.80"),
			new BigDecimal("10.000000")
		);

		ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
		verify(ledgerEntryRepository).save(captor.capture());
		assertThat(entry).isSameAs(captor.getValue());
		assertThat(entry.getUser()).isEqualTo(user);
		assertThat(entry.getPortfolio()).isEqualTo(portfolio);
		assertThat(entry.getOrder()).isEqualTo(fill.getOrder());
		assertThat(entry.getFill()).isEqualTo(fill);
		assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.BUY_FILL);
		assertThat(entry.getCashDelta()).isEqualByComparingTo("-1874.80");
		assertThat(entry.getSymbol()).isEqualTo(symbol);
		assertThat(entry.getQuantityDelta()).isEqualByComparingTo("10.000000");
		assertThat(entry.getPrice()).isEqualByComparingTo("187.480000");
		assertThat(entry.getMetadata()).containsEntry("orderSide", "BUY");
		assertThat(entry.getMetadata()).containsEntry("orderType", "MARKET");
		assertThat(entry.getMetadata()).containsEntry("fee", new BigDecimal("0.00"));
	}

	@Test
	void appendFillCreatesSellFillLedgerEntry() {
		Fill fill = fill(order(OrderSide.SELL), new BigDecimal("187.360000"), new BigDecimal("4.000000"));
		when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LedgerEntry entry = ledgerService.appendFill(
			portfolio,
			fill,
			new BigDecimal("749.44"),
			new BigDecimal("-4.000000")
		);

		assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.SELL_FILL);
		assertThat(entry.getCashDelta()).isEqualByComparingTo("749.44");
		assertThat(entry.getQuantityDelta()).isEqualByComparingTo("-4.000000");
		assertThat(entry.getMetadata()).containsEntry("orderSide", "SELL");
	}

	@Test
	void appendCashTransferCreatesCashLedgerEntry() {
		CashTransfer transfer = cashTransfer(CashTransferType.DEPOSIT, new BigDecimal("25000.00"));
		when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LedgerEntry entry = ledgerService.appendCashTransfer(
			portfolio,
			transfer,
			LedgerEntryType.CASH_DEPOSIT,
			new BigDecimal("25000.00")
		);

		assertThat(entry.getUser()).isEqualTo(user);
		assertThat(entry.getPortfolio()).isEqualTo(portfolio);
		assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.CASH_DEPOSIT);
		assertThat(entry.getCashDelta()).isEqualByComparingTo("25000.00");
		assertThat(entry.getSymbol()).isNull();
		assertThat(entry.getQuantityDelta()).isEqualByComparingTo("0");
		assertThat(entry.getPrice()).isNull();
		assertThat(entry.getMetadata()).containsEntry("transferId", transfer.getId().toString());
		assertThat(entry.getMetadata()).containsEntry("transferType", "DEPOSIT");
		assertThat(entry.getMetadata()).containsEntry("amount", new BigDecimal("25000.00"));
		assertThat(entry.getMetadata()).containsEntry("note", "demo cash");
	}

	private CashTransfer cashTransfer(CashTransferType transferType, BigDecimal amount) {
		CashTransfer transfer = new CashTransfer();
		transfer.setId(UUID.randomUUID());
		transfer.setUser(user);
		transfer.setPortfolio(portfolio);
		transfer.setTransferType(transferType);
		transfer.setAmount(amount);
		transfer.setStatus(CashTransferStatus.COMPLETED);
		transfer.setIdempotencyKey("cash-key-1");
		transfer.setNote("demo cash");
		return transfer;
	}

	private Fill fill(TradeOrder order, BigDecimal price, BigDecimal quantity) {
		Fill fill = new Fill();
		fill.setId(UUID.randomUUID());
		fill.setOrder(order);
		fill.setSymbol(symbol);
		fill.setPrice(price);
		fill.setQuantity(quantity);
		fill.setFee(new BigDecimal("0.00"));
		fill.setFilledAt(NOW);
		return fill;
	}

	private TradeOrder order(OrderSide side) {
		TradeOrder order = new TradeOrder();
		order.setId(UUID.randomUUID());
		order.setUser(user);
		order.setSymbol(symbol);
		order.setSide(side);
		order.setOrderType(OrderType.MARKET);
		order.setQuantity(new BigDecimal("1.000000"));
		order.setStatus(OrderStatus.FILLED);
		order.setIdempotencyKey("order-key-1");
		order.setCreatedAt(NOW);
		order.setUpdatedAt(NOW);
		return order;
	}

	private Portfolio portfolio() {
		Portfolio testPortfolio = new Portfolio();
		testPortfolio.setId(UUID.randomUUID());
		testPortfolio.setUser(user);
		testPortfolio.setCashBalance(new BigDecimal("100000.00"));
		testPortfolio.setBaseCurrency("USD");
		return testPortfolio;
	}

	private User user() {
		User testUser = new User();
		testUser.setId(USER_ID);
		testUser.setEmail("user@example.com");
		testUser.setRole(UserRole.USER);
		testUser.setPasswordHash("hash");
		return testUser;
	}

	private Symbol symbol(String ticker) {
		Symbol testSymbol = new Symbol();
		testSymbol.setId(UUID.randomUUID());
		testSymbol.setTicker(ticker);
		testSymbol.setName(ticker + " Inc.");
		testSymbol.setExchange("NASDAQ");
		testSymbol.setAssetType(AssetType.EQUITY);
		testSymbol.setCurrency("USD");
		testSymbol.setActive(true);
		return testSymbol;
	}
}
