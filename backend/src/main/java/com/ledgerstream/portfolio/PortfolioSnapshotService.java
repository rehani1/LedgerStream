package com.ledgerstream.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.PortfolioSnapshot;
import com.ledgerstream.domain.model.Position;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PortfolioSnapshotRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.portfolio.dto.PortfolioHistoryResponse;
import com.ledgerstream.portfolio.dto.PortfolioSnapshotResponse;
import com.ledgerstream.quotes.QuoteQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioSnapshotService {

	private static final int MONEY_SCALE = 2;
	private static final int MAX_HISTORY_PAGE_SIZE = 100;
	private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
	private static final RoundingMode ACCOUNTING_ROUNDING = RoundingMode.HALF_UP;

	private final PortfolioRepository portfolioRepository;
	private final PositionRepository positionRepository;
	private final PortfolioSnapshotRepository portfolioSnapshotRepository;
	private final QuoteQueryService quoteQueryService;
	private final Clock clock;

	public PortfolioSnapshotService(
		PortfolioRepository portfolioRepository,
		PositionRepository positionRepository,
		PortfolioSnapshotRepository portfolioSnapshotRepository,
		QuoteQueryService quoteQueryService,
		Clock clock
	) {
		this.portfolioRepository = portfolioRepository;
		this.positionRepository = positionRepository;
		this.portfolioSnapshotRepository = portfolioSnapshotRepository;
		this.quoteQueryService = quoteQueryService;
		this.clock = clock;
	}

	@Transactional
	public PortfolioSnapshot recordSnapshot(UUID userId) {
		Portfolio portfolio = portfolioRepository.findByUserId(userId)
			.orElseThrow(() -> new IllegalStateException("Portfolio not found for user " + userId));
		return recordSnapshot(portfolio, positionRepository.findByUserId(userId));
	}

	@Transactional
	public List<PortfolioSnapshot> recordSnapshotsForSymbol(String ticker) {
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

	@Transactional(readOnly = true)
	public PortfolioHistoryResponse listHistory(AuthenticatedUser authenticatedUser, int page, int size) {
		requirePortfolio(authenticatedUser.id());
		int normalizedPage = normalizePage(page);
		int normalizedSize = normalizeSize(size);
		Page<PortfolioSnapshot> snapshotPage = portfolioSnapshotRepository.findByUserIdOrderByCreatedAtDesc(
			authenticatedUser.id(),
			PageRequest.of(normalizedPage, normalizedSize)
		);
		return new PortfolioHistoryResponse(
			snapshotPage.getContent().stream().map(PortfolioSnapshotResponse::from).toList(),
			normalizedPage,
			normalizedSize,
			snapshotPage.getTotalElements(),
			snapshotPage.getTotalPages()
		);
	}

	private PortfolioSnapshot recordSnapshot(Portfolio portfolio, List<Position> positions) {
		PortfolioMetrics metrics = calculate(portfolio, positions);
		PortfolioSnapshot snapshot = new PortfolioSnapshot();
		snapshot.setUser(portfolio.getUser());
		snapshot.setPortfolio(portfolio);
		snapshot.setTotalEquity(metrics.totalEquity());
		snapshot.setCash(metrics.cash());
		snapshot.setMarketValue(metrics.marketValue());
		snapshot.setGrossExposure(metrics.grossExposure());
		snapshot.setRealizedPnl(metrics.realizedPnl());
		snapshot.setUnrealizedPnl(metrics.unrealizedPnl());
		snapshot.setCreatedAt(Instant.now(clock));
		return portfolioSnapshotRepository.save(snapshot);
	}

	private PortfolioMetrics calculate(Portfolio portfolio, List<Position> positions) {
		BigDecimal cash = money(portfolio.getCashBalance());
		BigDecimal marketValue = ZERO_MONEY;
		BigDecimal grossExposure = ZERO_MONEY;
		BigDecimal realizedPnl = ZERO_MONEY;
		BigDecimal unrealizedPnl = ZERO_MONEY;

		for (Position position : positions) {
			PositionValuation valuation = value(position);
			marketValue = money(marketValue.add(valuation.marketValue()));
			grossExposure = money(grossExposure.add(valuation.marketValue().abs()));
			realizedPnl = money(realizedPnl.add(position.getRealizedPnl()));
			unrealizedPnl = money(unrealizedPnl.add(valuation.unrealizedPnl()));
		}

		return new PortfolioMetrics(
			money(cash.add(marketValue)),
			cash,
			marketValue,
			grossExposure,
			realizedPnl,
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

	private Portfolio requirePortfolio(UUID userId) {
		return portfolioRepository.findByUserId(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
	}

	private int normalizePage(int page) {
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater");
		}
		return page;
	}

	private int normalizeSize(int size) {
		if (size < 1 || size > MAX_HISTORY_PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and 100");
		}
		return size;
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(MONEY_SCALE, ACCOUNTING_ROUNDING);
	}

	private record PositionValuation(BigDecimal marketValue, BigDecimal unrealizedPnl) {
	}

	private record PortfolioMetrics(
		BigDecimal totalEquity,
		BigDecimal cash,
		BigDecimal marketValue,
		BigDecimal grossExposure,
		BigDecimal realizedPnl,
		BigDecimal unrealizedPnl
	) {
	}
}
