package com.ledgerstream.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.Position;
import com.ledgerstream.domain.model.RiskSnapshot;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.domain.repository.RiskSnapshotRepository;
import com.ledgerstream.events.EventPublisher;
import com.ledgerstream.events.RiskUpdatedEvent;
import com.ledgerstream.quotes.QuoteQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RiskCalculationService {

	private static final int MONEY_SCALE = 2;
	private static final int PERCENT_SCALE = 4;
	private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
	private static final BigDecimal ZERO_PERCENT = new BigDecimal("0.0000");
	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
	private static final RoundingMode ACCOUNTING_ROUNDING = RoundingMode.HALF_UP;

	private final PortfolioRepository portfolioRepository;
	private final PositionRepository positionRepository;
	private final RiskSnapshotRepository riskSnapshotRepository;
	private final QuoteQueryService quoteQueryService;
	private final EventPublisher eventPublisher;
	private final Clock clock;

	public RiskCalculationService(
		PortfolioRepository portfolioRepository,
		PositionRepository positionRepository,
		RiskSnapshotRepository riskSnapshotRepository,
		QuoteQueryService quoteQueryService,
		EventPublisher eventPublisher,
		Clock clock
	) {
		this.portfolioRepository = portfolioRepository;
		this.positionRepository = positionRepository;
		this.riskSnapshotRepository = riskSnapshotRepository;
		this.quoteQueryService = quoteQueryService;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public RiskSnapshot recordSnapshot(UUID userId) {
		Portfolio portfolio = portfolioRepository.findByUserId(userId)
			.orElseThrow(() -> new IllegalStateException("Portfolio not found for user " + userId));
		return recordSnapshot(portfolio, positionRepository.findByUserId(userId));
	}

	@Transactional
	public List<RiskSnapshot> recordSnapshotsForSymbol(String ticker) {
		Set<UUID> affectedUsers = new LinkedHashSet<>();
		for (Position position : positionRepository.findBySymbolTicker(ticker)) {
			if (position.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
				affectedUsers.add(position.getUser().getId());
			}
		}

		return affectedUsers.stream()
			.flatMap(userId -> portfolioRepository.findByUserId(userId).stream())
			.map(portfolio -> recordSnapshot(portfolio, positionRepository.findByUserId(portfolio.getUser().getId())))
			.toList();
	}

	private RiskSnapshot recordSnapshot(Portfolio portfolio, List<Position> positions) {
		RiskMetrics metrics = calculate(portfolio, positions);
		RiskSnapshot snapshot = new RiskSnapshot();
		snapshot.setUser(portfolio.getUser());
		snapshot.setTotalEquity(metrics.totalEquity());
		snapshot.setCash(metrics.cash());
		snapshot.setGrossExposure(metrics.grossExposure());
		snapshot.setLargestPositionPct(metrics.largestPositionPct());
		snapshot.setUnrealizedPnl(metrics.unrealizedPnl());
		snapshot.setCreatedAt(Instant.now(clock));

		RiskSnapshot savedSnapshot = riskSnapshotRepository.save(snapshot);
		eventPublisher.publishRiskUpdated(toRiskUpdatedEvent(savedSnapshot));
		return savedSnapshot;
	}

	private RiskMetrics calculate(Portfolio portfolio, List<Position> positions) {
		BigDecimal cash = money(portfolio.getCashBalance());
		BigDecimal netMarketValue = ZERO_MONEY;
		BigDecimal grossExposure = ZERO_MONEY;
		BigDecimal largestPosition = ZERO_MONEY;
		BigDecimal unrealizedPnl = ZERO_MONEY;

		for (Position position : positions) {
			PositionValuation valuation = value(position);
			netMarketValue = money(netMarketValue.add(valuation.marketValue()));
			grossExposure = money(grossExposure.add(valuation.marketValue().abs()));
			largestPosition = largestPosition.max(valuation.marketValue().abs());
			unrealizedPnl = money(unrealizedPnl.add(valuation.unrealizedPnl()));
		}

		BigDecimal totalEquity = money(cash.add(netMarketValue));
		return new RiskMetrics(
			totalEquity,
			cash,
			grossExposure,
			largestPositionPct(largestPosition, totalEquity),
			unrealizedPnl
		);
	}

	private PositionValuation value(Position position) {
		BigDecimal quantity = position.getQuantity();
		Optional<BigDecimal> latestPrice = latestPrice(position.getSymbol().getTicker());
		BigDecimal valuationPrice = latestPrice.orElse(position.getAvgCost());
		BigDecimal marketValue = money(valuationPrice.multiply(quantity));
		BigDecimal unrealizedPnl = latestPrice
			.map(price -> money(price.subtract(position.getAvgCost()).multiply(quantity)))
			.orElse(ZERO_MONEY);
		return new PositionValuation(marketValue, unrealizedPnl);
	}

	private Optional<BigDecimal> latestPrice(String ticker) {
		try {
			return Optional.ofNullable(quoteQueryService.getLatestQuote(ticker).last());
		} catch (ResponseStatusException ex) {
			if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	private BigDecimal largestPositionPct(BigDecimal largestPosition, BigDecimal totalEquity) {
		if (totalEquity.compareTo(BigDecimal.ZERO) <= 0 || largestPosition.compareTo(BigDecimal.ZERO) == 0) {
			return ZERO_PERCENT;
		}

		return largestPosition
			.multiply(ONE_HUNDRED)
			.divide(totalEquity, PERCENT_SCALE, ACCOUNTING_ROUNDING);
	}

	private RiskUpdatedEvent toRiskUpdatedEvent(RiskSnapshot snapshot) {
		return new RiskUpdatedEvent(
			UUID.randomUUID(),
			snapshot.getUser().getId(),
			snapshot.getTotalEquity(),
			snapshot.getGrossExposure(),
			snapshot.getLargestPositionPct(),
			snapshot.getUnrealizedPnl(),
			snapshot.getCreatedAt()
		);
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(MONEY_SCALE, ACCOUNTING_ROUNDING);
	}

	private record PositionValuation(BigDecimal marketValue, BigDecimal unrealizedPnl) {
	}

	private record RiskMetrics(
		BigDecimal totalEquity,
		BigDecimal cash,
		BigDecimal grossExposure,
		BigDecimal largestPositionPct,
		BigDecimal unrealizedPnl
	) {
	}
}
