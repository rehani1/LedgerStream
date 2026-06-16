package com.ledgerstream.orders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

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
import com.ledgerstream.quotes.QuoteQueryService;
import com.ledgerstream.quotes.dto.QuoteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderExecutionService {

	static final BigDecimal ZERO_FEE = new BigDecimal("0.00");

	private final OrderRepository orderRepository;
	private final FillRepository fillRepository;
	private final PortfolioRepository portfolioRepository;
	private final PositionRepository positionRepository;
	private final QuoteQueryService quoteQueryService;
	private final EventPublisher eventPublisher;
	private final Clock clock;

	public OrderExecutionService(
		OrderRepository orderRepository,
		FillRepository fillRepository,
		PortfolioRepository portfolioRepository,
		PositionRepository positionRepository,
		QuoteQueryService quoteQueryService,
		EventPublisher eventPublisher,
		Clock clock
	) {
		this.orderRepository = orderRepository;
		this.fillRepository = fillRepository;
		this.portfolioRepository = portfolioRepository;
		this.positionRepository = positionRepository;
		this.quoteQueryService = quoteQueryService;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public void execute(OrderCreatedEvent event) {
		orderRepository.findById(event.orderId()).ifPresent(this::execute);
	}

	@Transactional
	public void execute(TradeOrder order) {
		if (order.getStatus() != OrderStatus.PENDING || order.getOrderType() != OrderType.MARKET) {
			return;
		}

		QuoteResponse quote = latestQuote(order);
		if (quote == null) {
			reject(order, "No market quote available");
			return;
		}

		BigDecimal executionPrice = executionPrice(order.getSide(), quote);
		if (executionPrice == null || executionPrice.compareTo(BigDecimal.ZERO) <= 0) {
			reject(order, "No executable market price available");
			return;
		}

		if (order.getSide() == OrderSide.BUY && !hasSufficientCash(order, executionPrice)) {
			reject(order, "Insufficient cash");
			return;
		}

		if (order.getSide() == OrderSide.SELL && !hasSufficientShares(order)) {
			reject(order, "Insufficient shares");
			return;
		}

		Fill fill = createFill(order, executionPrice);
		order.setStatus(OrderStatus.FILLED);
		orderRepository.save(order);
		Fill savedFill = fillRepository.save(fill);
		eventPublisher.publishOrderFilled(toOrderFilledEvent(savedFill));
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

	private boolean hasSufficientCash(TradeOrder order, BigDecimal executionPrice) {
		Portfolio portfolio = portfolioRepository.findByUserId(order.getUser().getId()).orElse(null);
		if (portfolio == null) {
			return false;
		}
		BigDecimal notional = executionPrice.multiply(order.getQuantity()).add(ZERO_FEE);
		return portfolio.getCashBalance().compareTo(notional) >= 0;
	}

	private boolean hasSufficientShares(TradeOrder order) {
		Position position = positionRepository
			.findByUserIdAndSymbolTicker(order.getUser().getId(), order.getSymbol().getTicker())
			.orElse(null);
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

	private void reject(TradeOrder order, String reason) {
		order.setStatus(OrderStatus.REJECTED);
		order.setRejectionReason(reason);
		orderRepository.save(order);
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
}
