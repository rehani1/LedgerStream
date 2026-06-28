package com.ledgerstream.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.LedgerEntry;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.Position;
import com.ledgerstream.domain.repository.LedgerEntryRepository;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.metrics.LedgerStreamMetrics;
import com.ledgerstream.portfolio.dto.LedgerEntryResponse;
import com.ledgerstream.portfolio.dto.LedgerPageResponse;
import com.ledgerstream.portfolio.dto.PortfolioPositionResponse;
import com.ledgerstream.portfolio.dto.PortfolioSummaryResponse;
import com.ledgerstream.quotes.QuoteQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioQueryService {

	private static final int MONEY_SCALE = 2;
	private static final int MAX_LEDGER_PAGE_SIZE = 100;
	private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
	private static final RoundingMode ACCOUNTING_ROUNDING = RoundingMode.HALF_UP;
	private static final String LATEST_QUOTE_SOURCE = "LATEST_QUOTE";
	private static final String COST_BASIS_FALLBACK_SOURCE = "COST_BASIS_FALLBACK";

	private final PortfolioRepository portfolioRepository;
	private final PositionRepository positionRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final QuoteQueryService quoteQueryService;
	private final LedgerStreamMetrics metrics;

	public PortfolioQueryService(
		PortfolioRepository portfolioRepository,
		PositionRepository positionRepository,
		LedgerEntryRepository ledgerEntryRepository,
		QuoteQueryService quoteQueryService,
		LedgerStreamMetrics metrics
	) {
		this.portfolioRepository = portfolioRepository;
		this.positionRepository = positionRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.quoteQueryService = quoteQueryService;
		this.metrics = metrics;
	}

	@Transactional(readOnly = true)
	public PortfolioSummaryResponse getPortfolio(AuthenticatedUser authenticatedUser) {
		return metrics.recordPortfolioCalculation(() -> calculatePortfolio(authenticatedUser));
	}

	@Transactional(readOnly = true)
	public List<PortfolioPositionResponse> listPositions(AuthenticatedUser authenticatedUser) {
		return metrics.recordPortfolioCalculation(() -> calculatePositions(authenticatedUser));
	}

	@Transactional(readOnly = true)
	public LedgerPageResponse listLedger(AuthenticatedUser authenticatedUser, int page, int size) {
		requirePortfolio(authenticatedUser);
		int normalizedPage = normalizePage(page);
		int normalizedSize = normalizeSize(size);
		Page<LedgerEntry> ledgerPage = ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(
			authenticatedUser.id(),
			PageRequest.of(normalizedPage, normalizedSize)
		);
		return new LedgerPageResponse(
			ledgerPage.getContent().stream().map(LedgerEntryResponse::from).toList(),
			normalizedPage,
			normalizedSize,
			ledgerPage.getTotalElements(),
			ledgerPage.getTotalPages()
		);
	}

	private PortfolioSummaryResponse calculatePortfolio(AuthenticatedUser authenticatedUser) {
		Portfolio portfolio = requirePortfolio(authenticatedUser);
		List<PortfolioPositionResponse> positions = listPositionResponses(authenticatedUser);
		BigDecimal marketValue = sum(positions.stream().map(PortfolioPositionResponse::marketValue).toList());
		BigDecimal realizedPnl = sum(positions.stream().map(PortfolioPositionResponse::realizedPnl).toList());
		BigDecimal unrealizedPnl = sum(positions.stream().map(PortfolioPositionResponse::unrealizedPnl).toList());
		int pricedPositions = (int) positions.stream()
			.filter(position -> LATEST_QUOTE_SOURCE.equals(position.valuationSource()))
			.count();

		return new PortfolioSummaryResponse(
			portfolio.getId(),
			portfolio.getBaseCurrency(),
			money(portfolio.getCashBalance()),
			marketValue,
			money(portfolio.getCashBalance().add(marketValue)),
			realizedPnl,
			unrealizedPnl,
			positions.size(),
			pricedPositions,
			portfolio.getUpdatedAt()
		);
	}

	private List<PortfolioPositionResponse> calculatePositions(AuthenticatedUser authenticatedUser) {
		requirePortfolio(authenticatedUser);
		return listPositionResponses(authenticatedUser);
	}

	private Portfolio requirePortfolio(AuthenticatedUser authenticatedUser) {
		return portfolioRepository.findByUserId(authenticatedUser.id())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
	}

	private List<PortfolioPositionResponse> listPositionResponses(AuthenticatedUser authenticatedUser) {
		return positionRepository.findByUserId(authenticatedUser.id()).stream()
			.map(this::toPositionResponse)
			.toList();
	}

	private PortfolioPositionResponse toPositionResponse(Position position) {
		BigDecimal quantity = position.getQuantity();
		BigDecimal costBasis = money(position.getAvgCost().multiply(quantity));
		Optional<BigDecimal> latestPrice = latestPrice(position.getSymbol().getTicker());
		BigDecimal valuationPrice = latestPrice.orElse(position.getAvgCost());
		String valuationSource = latestPrice.isPresent() ? LATEST_QUOTE_SOURCE : COST_BASIS_FALLBACK_SOURCE;
		BigDecimal marketValue = money(valuationPrice.multiply(quantity));
		BigDecimal unrealizedPnl = latestPrice
			.map(price -> money(price.subtract(position.getAvgCost()).multiply(quantity)))
			.orElse(ZERO_MONEY);

		return new PortfolioPositionResponse(
			position.getId(),
			position.getSymbol().getTicker(),
			quantity,
			position.getAvgCost(),
			latestPrice.orElse(null),
			valuationPrice,
			valuationSource,
			marketValue,
			costBasis,
			unrealizedPnl,
			money(position.getRealizedPnl()),
			position.getUpdatedAt()
		);
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

	private BigDecimal sum(List<BigDecimal> values) {
		return money(values.stream().reduce(ZERO_MONEY, BigDecimal::add));
	}

	private int normalizePage(int page) {
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater");
		}
		return page;
	}

	private int normalizeSize(int size) {
		if (size < 1 || size > MAX_LEDGER_PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and 100");
		}
		return size;
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(MONEY_SCALE, ACCOUNTING_ROUNDING);
	}
}
