package com.ledgerstream.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.domain.model.Fill;
import com.ledgerstream.domain.model.OrderSide;
import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.OrderType;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.Position;
import com.ledgerstream.domain.model.TradeOrder;
import com.ledgerstream.domain.repository.FillRepository;
import com.ledgerstream.domain.repository.OrderRepository;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.events.EventPublisher;
import com.ledgerstream.events.OrderCreatedEvent;
import com.ledgerstream.events.OrderFilledEvent;
import com.ledgerstream.logging.MdcScope;
import com.ledgerstream.portfolio.PortfolioLedgerService;
import com.ledgerstream.quotes.QuoteQueryService;
import com.ledgerstream.quotes.dto.QuoteResponse;
import com.ledgerstream.risk.RiskCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderExecutionService {

	static final int MONEY_SCALE = 2;
	static final int PRICE_SCALE = 6;
	static final int QUANTITY_SCALE = 6;
	static final BigDecimal ZERO_FEE = new BigDecimal("0.00");
	static final BigDecimal ZERO_PRICE = new BigDecimal("0.000000");
	static final BigDecimal ZERO_QUANTITY = new BigDecimal("0.000000");

	private static final RoundingMode ACCOUNTING_ROUNDING = RoundingMode.HALF_UP;
	private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);

	private final OrderRepository orderRepository;
	private final FillRepository fillRepository;
	private final PortfolioRepository portfolioRepository;
	private final PositionRepository positionRepository;
	private final QuoteQueryService quoteQueryService;
	private final PortfolioLedgerService portfolioLedgerService;
	private final RiskCalculationService riskCalculationService;
	private final EventPublisher eventPublisher;
	private final AuditService auditService;
	private final Clock clock;

	public OrderExecutionService(
		OrderRepository orderRepository,
		FillRepository fillRepository,
		PortfolioRepository portfolioRepository,
		PositionRepository positionRepository,
		QuoteQueryService quoteQueryService,
		PortfolioLedgerService portfolioLedgerService,
		RiskCalculationService riskCalculationService,
		EventPublisher eventPublisher,
		AuditService auditService,
		Clock clock
	) {
		this.orderRepository = orderRepository;
		this.fillRepository = fillRepository;
		this.portfolioRepository = portfolioRepository;
		this.positionRepository = positionRepository;
		this.quoteQueryService = quoteQueryService;
		this.portfolioLedgerService = portfolioLedgerService;
		this.riskCalculationService = riskCalculationService;
		this.eventPublisher = eventPublisher;
		this.auditService = auditService;
		this.clock = clock;
	}

	@Transactional
	public void execute(OrderCreatedEvent event) {
		orderRepository.findById(event.orderId()).ifPresent(order -> execute(order, event.requestId()));
	}

	@Transactional
	public void execute(TradeOrder order) {
		execute(order, null);
	}

	private void execute(TradeOrder order, String requestId) {
		if (order.getStatus() != OrderStatus.PENDING || order.getOrderType() != OrderType.MARKET) {
			return;
		}

		QuoteResponse quote = latestQuote(order);
		if (quote == null) {
			reject(order, "No market quote available", requestId);
			return;
		}

		BigDecimal executionPrice = executionPrice(order.getSide(), quote);
		if (executionPrice == null || executionPrice.compareTo(BigDecimal.ZERO) <= 0) {
			reject(order, "No executable market price available", requestId);
			return;
		}

		Portfolio portfolio;
		Position sellPosition = null;
		if (order.getSide() == OrderSide.BUY) {
			portfolio = portfolioRepository.findByUserId(order.getUser().getId()).orElse(null);
			if (portfolio == null) {
				reject(order, "Portfolio not found", requestId);
				return;
			}
			if (!hasSufficientCash(portfolio, order, executionPrice)) {
				reject(order, "Insufficient cash", requestId);
				return;
			}
		} else {
			sellPosition = positionRepository
				.findByUserIdAndSymbolTicker(order.getUser().getId(), order.getSymbol().getTicker())
				.orElse(null);
			if (!hasSufficientShares(sellPosition, order)) {
				reject(order, "Insufficient shares", requestId);
				return;
			}
			portfolio = portfolioRepository.findByUserId(order.getUser().getId()).orElse(null);
			if (portfolio == null) {
				reject(order, "Portfolio not found", requestId);
				return;
			}
		}

		Fill fill = createFill(order, executionPrice);
		order.setStatus(OrderStatus.FILLED);
		orderRepository.save(order);
		Fill savedFill = fillRepository.save(fill);
		applyPortfolioUpdate(portfolio, savedFill, sellPosition);
		riskCalculationService.recordSnapshot(order.getUser().getId());
		eventPublisher.publishOrderFilled(toOrderFilledEvent(savedFill));
		try (MdcScope ignored = orderLogContext(order, "order.filled")) {
			log.info("order_filled");
		}
	}

	private QuoteResponse latestQuote(TradeOrder order) {
		try {
			return quoteQueryService.getLatestQuote(order.getSymbol().getTicker());
		} catch (ResponseStatusException ex) {
			if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
				return null;
			}
			throw ex;
		}
	}

	private BigDecimal executionPrice(OrderSide side, QuoteResponse quote) {
		if (side == OrderSide.BUY) {
			return quote.ask() == null ? quote.last() : quote.ask();
		}
		return quote.bid() == null ? quote.last() : quote.bid();
	}

	private boolean hasSufficientCash(Portfolio portfolio, TradeOrder order, BigDecimal executionPrice) {
		BigDecimal totalCost = money(executionPrice.multiply(order.getQuantity()).add(ZERO_FEE));
		return portfolio.getCashBalance().compareTo(totalCost) >= 0;
	}

	private boolean hasSufficientShares(Position position, TradeOrder order) {
		return position != null && position.getQuantity().compareTo(order.getQuantity()) >= 0;
	}

	private Fill createFill(TradeOrder order, BigDecimal executionPrice) {
		Fill fill = new Fill();
		fill.setOrder(order);
		fill.setSymbol(order.getSymbol());
		fill.setPrice(executionPrice);
		fill.setQuantity(order.getQuantity());
		fill.setFee(ZERO_FEE);
		fill.setFilledAt(Instant.now(clock));
		return fill;
	}

	private void applyPortfolioUpdate(Portfolio portfolio, Fill fill, Position sellPosition) {
		if (fill.getOrder().getSide() == OrderSide.BUY) {
			applyBuy(portfolio, fill);
		} else {
			applySell(portfolio, fill, sellPosition);
		}
		portfolioRepository.save(portfolio);
	}

	private void applyBuy(Portfolio portfolio, Fill fill) {
		BigDecimal totalCost = money(notional(fill).add(fill.getFee()));
		portfolio.setCashBalance(money(portfolio.getCashBalance().subtract(totalCost)));

		Position position = positionRepository
			.findByUserIdAndSymbolTicker(fill.getOrder().getUser().getId(), fill.getSymbol().getTicker())
			.orElseGet(() -> newPosition(fill));

		BigDecimal existingQuantity = quantity(position.getQuantity());
		BigDecimal fillQuantity = quantity(fill.getQuantity());
		BigDecimal updatedQuantity = quantity(existingQuantity.add(fillQuantity));
		BigDecimal existingCostBasis = position.getAvgCost().multiply(existingQuantity);
		BigDecimal fillCostBasis = fill.getPrice().multiply(fillQuantity);
		BigDecimal updatedAvgCost = price(existingCostBasis.add(fillCostBasis).divide(
			updatedQuantity,
			PRICE_SCALE,
			ACCOUNTING_ROUNDING
		));

		position.setQuantity(updatedQuantity);
		position.setAvgCost(updatedAvgCost);
		positionRepository.save(position);
		portfolioLedgerService.appendFill(portfolio, fill, totalCost.negate(), fillQuantity);
	}

	private void applySell(Portfolio portfolio, Fill fill, Position position) {
		BigDecimal proceeds = money(notional(fill).subtract(fill.getFee()));
		portfolio.setCashBalance(money(portfolio.getCashBalance().add(proceeds)));

		BigDecimal fillQuantity = quantity(fill.getQuantity());
		BigDecimal updatedQuantity = quantity(position.getQuantity().subtract(fillQuantity));
		BigDecimal realizedPnl = money(fill.getPrice()
			.subtract(position.getAvgCost())
			.multiply(fillQuantity)
			.subtract(fill.getFee()));

		position.setQuantity(updatedQuantity);
		position.setRealizedPnl(money(position.getRealizedPnl().add(realizedPnl)));
		if (updatedQuantity.compareTo(BigDecimal.ZERO) == 0) {
			position.setAvgCost(ZERO_PRICE);
		}
		positionRepository.save(position);
		portfolioLedgerService.appendFill(portfolio, fill, proceeds, fillQuantity.negate());
	}

	private Position newPosition(Fill fill) {
		Position position = new Position();
		position.setUser(fill.getOrder().getUser());
		position.setSymbol(fill.getSymbol());
		position.setQuantity(ZERO_QUANTITY);
		position.setAvgCost(ZERO_PRICE);
		position.setRealizedPnl(ZERO_FEE);
		position.setUpdatedAt(Instant.now(clock));
		return position;
	}

	private BigDecimal notional(Fill fill) {
		return fill.getPrice().multiply(fill.getQuantity());
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(MONEY_SCALE, ACCOUNTING_ROUNDING);
	}

	private BigDecimal price(BigDecimal value) {
		return value.setScale(PRICE_SCALE, ACCOUNTING_ROUNDING);
	}

	private BigDecimal quantity(BigDecimal value) {
		return value.setScale(QUANTITY_SCALE, ACCOUNTING_ROUNDING);
	}

	private void reject(TradeOrder order, String reason, String requestId) {
		order.setStatus(OrderStatus.REJECTED);
		order.setRejectionReason(reason);
		orderRepository.save(order);
		auditService.record(order.getUser(), "ORDER_REJECTED", requestId, orderRejectionMetadata(order, reason));
		try (MdcScope ignored = orderLogContext(order, "order.rejected")) {
			log.info("order_rejected reason={}", reason);
		}
	}

	private Map<String, Object> orderRejectionMetadata(TradeOrder order, String reason) {
		return Map.of(
			"orderId", order.getId().toString(),
			"symbol", order.getSymbol().getTicker(),
			"side", order.getSide().name(),
			"orderType", order.getOrderType().name(),
			"quantity", order.getQuantity(),
			"reason", reason
		);
	}

	private OrderFilledEvent toOrderFilledEvent(Fill fill) {
		return new OrderFilledEvent(
			UUID.randomUUID(),
			fill.getOrder().getId(),
			fill.getId(),
			fill.getOrder().getUser().getId(),
			fill.getSymbol().getTicker(),
			fill.getOrder().getSide(),
			fill.getQuantity(),
			fill.getPrice(),
			fill.getFee(),
			fill.getFilledAt()
		);
	}

	private MdcScope orderLogContext(TradeOrder order, String eventType) {
		return MdcScope.put(Map.of(
			"userId", order.getUser().getId().toString(),
			"orderId", order.getId().toString(),
			"symbol", order.getSymbol().getTicker(),
			"eventType", eventType
		));
	}
}
