package com.ledgerstream.security;

import java.util.UUID;
import java.util.function.BiPredicate;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.repository.LedgerEntryRepository;
import com.ledgerstream.domain.repository.OrderRepository;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.domain.repository.RiskSnapshotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessControlService {

	private final OrderRepository orderRepository;
	private final PortfolioRepository portfolioRepository;
	private final PositionRepository positionRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final RiskSnapshotRepository riskSnapshotRepository;

	public AccessControlService(
		OrderRepository orderRepository,
		PortfolioRepository portfolioRepository,
		PositionRepository positionRepository,
		LedgerEntryRepository ledgerEntryRepository,
		RiskSnapshotRepository riskSnapshotRepository
	) {
		this.orderRepository = orderRepository;
		this.portfolioRepository = portfolioRepository;
		this.positionRepository = positionRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.riskSnapshotRepository = riskSnapshotRepository;
	}

	public UUID requireUserId(AuthenticatedUser authenticatedUser) {
		if (authenticatedUser == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
		}
		return authenticatedUser.id();
	}

	@Transactional(readOnly = true)
	public void requireOrderOwner(AuthenticatedUser authenticatedUser, UUID orderId) {
		requireOwnedResource(authenticatedUser, orderId, "Order", orderRepository::existsByIdAndUserId);
	}

	@Transactional(readOnly = true)
	public void requirePortfolioOwner(AuthenticatedUser authenticatedUser, UUID portfolioId) {
		requireOwnedResource(authenticatedUser, portfolioId, "Portfolio", portfolioRepository::existsByIdAndUserId);
	}

	@Transactional(readOnly = true)
	public void requirePositionOwner(AuthenticatedUser authenticatedUser, UUID positionId) {
		requireOwnedResource(authenticatedUser, positionId, "Position", positionRepository::existsByIdAndUserId);
	}

	@Transactional(readOnly = true)
	public void requireLedgerEntryOwner(AuthenticatedUser authenticatedUser, UUID ledgerEntryId) {
		requireOwnedResource(authenticatedUser, ledgerEntryId, "Ledger entry", ledgerEntryRepository::existsByIdAndUserId);
	}

	@Transactional(readOnly = true)
	public void requireRiskSnapshotOwner(AuthenticatedUser authenticatedUser, UUID riskSnapshotId) {
		requireOwnedResource(authenticatedUser, riskSnapshotId, "Risk snapshot", riskSnapshotRepository::existsByIdAndUserId);
	}

	private void requireOwnedResource(
		AuthenticatedUser authenticatedUser,
		UUID resourceId,
		String resourceName,
		BiPredicate<UUID, UUID> ownershipCheck
	) {
		UUID userId = requireUserId(authenticatedUser);
		if (!ownershipCheck.test(resourceId, userId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, resourceName + " not found");
		}
	}
}
