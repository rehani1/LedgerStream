package com.ledgerstream.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.RiskSnapshot;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.RiskSnapshotRepository;
import com.ledgerstream.risk.dto.RiskHistoryResponse;
import com.ledgerstream.risk.dto.RiskSnapshotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RiskQueryServiceTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");

	@Mock
	private RiskSnapshotRepository riskSnapshotRepository;

	private RiskQueryService riskQueryService;
	private AuthenticatedUser authenticatedUser;
	private User user;

	@BeforeEach
	void setUp() {
		riskQueryService = new RiskQueryService(riskSnapshotRepository);
		authenticatedUser = new AuthenticatedUser(USER_ID, "user@example.com", UserRole.USER);
		user = user();
	}

	@Test
	void getLatestRiskReturnsMostRecentUserSnapshot() {
		RiskSnapshot snapshot = riskSnapshot(NOW);
		when(riskSnapshotRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(snapshot));

		RiskSnapshotResponse response = riskQueryService.getLatestRisk(authenticatedUser);

		assertThat(response.id()).isEqualTo(snapshot.getId());
		assertThat(response.totalEquity()).isEqualByComparingTo("125000.00");
		assertThat(response.cash()).isEqualByComparingTo("100000.00");
		assertThat(response.grossExposure()).isEqualByComparingTo("25000.00");
		assertThat(response.largestPositionPct()).isEqualByComparingTo("20.0000");
		assertThat(response.unrealizedPnl()).isEqualByComparingTo("750.00");
		assertThat(response.createdAt()).isEqualTo(NOW);
	}

	@Test
	void getLatestRiskReturnsNotFoundWhenNoSnapshotExists() {
		when(riskSnapshotRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> riskQueryService.getLatestRisk(authenticatedUser))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Risk snapshot not found");
	}

	@Test
	void listRiskHistoryUsesUserScopedPagination() {
		RiskSnapshot first = riskSnapshot(NOW);
		RiskSnapshot second = riskSnapshot(NOW.minusSeconds(60));
		when(riskSnapshotRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, PageRequest.of(0, 2)))
			.thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 2), 3));

		RiskHistoryResponse response = riskQueryService.listRiskHistory(authenticatedUser, 0, 2);

		assertThat(response.page()).isEqualTo(0);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(3);
		assertThat(response.totalPages()).isEqualTo(2);
		assertThat(response.snapshots()).hasSize(2);
		verify(riskSnapshotRepository).findByUserIdOrderByCreatedAtDesc(USER_ID, PageRequest.of(0, 2));
	}

	@Test
	void listRiskHistoryRejectsInvalidSize() {
		assertThatThrownBy(() -> riskQueryService.listRiskHistory(authenticatedUser, 0, 101))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Size must be between 1 and 100");
	}

	private RiskSnapshot riskSnapshot(Instant createdAt) {
		RiskSnapshot snapshot = new RiskSnapshot();
		snapshot.setId(UUID.randomUUID());
		snapshot.setUser(user);
		snapshot.setTotalEquity(new BigDecimal("125000.00"));
		snapshot.setCash(new BigDecimal("100000.00"));
		snapshot.setGrossExposure(new BigDecimal("25000.00"));
		snapshot.setLargestPositionPct(new BigDecimal("20.0000"));
		snapshot.setUnrealizedPnl(new BigDecimal("750.00"));
		snapshot.setCreatedAt(createdAt);
		return snapshot;
	}

	private User user() {
		User testUser = new User();
		testUser.setId(USER_ID);
		testUser.setEmail("user@example.com");
		testUser.setRole(UserRole.USER);
		testUser.setPasswordHash("hash");
		return testUser;
	}
}
