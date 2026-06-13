package com.ledgerstream.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.LedgerEntryRepository;
import com.ledgerstream.domain.repository.OrderRepository;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.PositionRepository;
import com.ledgerstream.domain.repository.RiskSnapshotRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private PositionRepository positionRepository;

	@Mock
	private LedgerEntryRepository ledgerEntryRepository;

	@Mock
	private RiskSnapshotRepository riskSnapshotRepository;

	private AccessControlService accessControlService;

	@BeforeEach
	void setUp() {
		accessControlService = new AccessControlService(
			orderRepository,
			portfolioRepository,
			positionRepository,
			ledgerEntryRepository,
			riskSnapshotRepository
		);
	}

	@Test
	void requireUserIdRejectsMissingPrincipal() {
		assertThatThrownBy(() -> accessControlService.requireUserId(null))
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
				assertThat(ex.getReason()).isEqualTo("Authentication required");
			});
	}

	@Test
	void requireOrderOwnerAllowsOwnedOrder() {
		AuthenticatedUser user = authenticatedUser();
		UUID orderId = UUID.randomUUID();
		when(orderRepository.existsByIdAndUserId(orderId, user.id())).thenReturn(true);

		assertThatCode(() -> accessControlService.requireOrderOwner(user, orderId)).doesNotThrowAnyException();
	}

	@Test
	void ownershipChecksDenyResourcesNotOwnedByPrincipal() {
		AuthenticatedUser user = authenticatedUser();
		assertNotFound(() -> accessControlService.requireOrderOwner(user, UUID.randomUUID()), "Order not found");
		assertNotFound(() -> accessControlService.requirePortfolioOwner(user, UUID.randomUUID()), "Portfolio not found");
		assertNotFound(() -> accessControlService.requirePositionOwner(user, UUID.randomUUID()), "Position not found");
		assertNotFound(() -> accessControlService.requireLedgerEntryOwner(user, UUID.randomUUID()), "Ledger entry not found");
		assertNotFound(() -> accessControlService.requireRiskSnapshotOwner(user, UUID.randomUUID()), "Risk snapshot not found");
	}

	private void assertNotFound(ThrowingCallable callable, String reason) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
				assertThat(ex.getReason()).isEqualTo(reason);
			});
	}

	private AuthenticatedUser authenticatedUser() {
		return new AuthenticatedUser(UUID.randomUUID(), "user@example.com", UserRole.USER);
	}
}
