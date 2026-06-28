package com.ledgerstream.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayControlServiceTest {

	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private UserRepository userRepository;

	@Mock
	private AuditService auditService;

	private ReplayControlService replayControlService;

	@BeforeEach
	void setUp() {
		replayControlService = new ReplayControlService(userRepository, auditService);
	}

	@Test
	void currentStatusStartsStopped() {
		ReplayControlResponse response = replayControlService.currentStatus();

		assertThat(response.status()).isEqualTo("STOPPED");
		assertThat(response.mode()).isEqualTo("backend_state");
		assertThat(response.updatedAt()).isNotNull();
	}

	@Test
	void startSetsRunningAndAuditsAdminAction() {
		User admin = adminUser();
		when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

		ReplayControlResponse response = replayControlService.start(authenticatedAdmin(), "request-1");

		assertThat(response.status()).isEqualTo("RUNNING");
		assertThat(replayControlService.currentStatus().status()).isEqualTo("RUNNING");
		verify(auditService).record(
			eq(admin),
			eq("MARKET_REPLAY_STARTED"),
			eq("request-1"),
			eq(Map.of("mode", "backend_state", "status", "RUNNING"))
		);
	}

	@Test
	void stopSetsStoppedAndAuditsAdminAction() {
		User admin = adminUser();
		when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

		ReplayControlResponse response = replayControlService.stop(authenticatedAdmin(), "request-2");

		assertThat(response.status()).isEqualTo("STOPPED");
		verify(auditService).record(
			eq(admin),
			eq("MARKET_REPLAY_STOPPED"),
			eq("request-2"),
			eq(Map.of("mode", "backend_state", "status", "STOPPED"))
		);
	}

	private AuthenticatedUser authenticatedAdmin() {
		return new AuthenticatedUser(ADMIN_ID, "admin@example.com", UserRole.ADMIN);
	}

	private User adminUser() {
		User user = new User();
		user.setEmail("admin@example.com");
		user.setPasswordHash("hash");
		user.setRole(UserRole.ADMIN);
		user.setId(ADMIN_ID);
		return user;
	}
}
