package com.ledgerstream.admin;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.ledgerstream.audit.AuditService;
import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplayControlService {

	private static final String MODE = "backend_state";
	private static final String RUNNING = "RUNNING";
	private static final String STOPPED = "STOPPED";

	private final UserRepository userRepository;
	private final AuditService auditService;
	private final AtomicReference<ReplayState> replayState = new AtomicReference<>(
		new ReplayState(
			STOPPED,
			"Replay state is stopped.",
			Instant.now()
		)
	);

	public ReplayControlService(UserRepository userRepository, AuditService auditService) {
		this.userRepository = userRepository;
		this.auditService = auditService;
	}

	public ReplayControlResponse currentStatus() {
		return replayState.get().toResponse();
	}

	@Transactional
	public ReplayControlResponse start(AuthenticatedUser admin, String requestId) {
		ReplayState next = new ReplayState(
			RUNNING,
			"Replay state is running. The local market-data worker publishes ticks from the configured fixture.",
			Instant.now()
		);
		recordAudit(admin, requestId, "MARKET_REPLAY_STARTED", next);
		replayState.set(next);
		return next.toResponse();
	}

	@Transactional
	public ReplayControlResponse stop(AuthenticatedUser admin, String requestId) {
		ReplayState next = new ReplayState(
			STOPPED,
			"Replay state is stopped. Existing quote, portfolio, and risk data remain available.",
			Instant.now()
		);
		recordAudit(admin, requestId, "MARKET_REPLAY_STOPPED", next);
		replayState.set(next);
		return next.toResponse();
	}

	private void recordAudit(AuthenticatedUser admin, String requestId, String action, ReplayState state) {
		User user = admin == null ? null : userRepository.findById(admin.id()).orElse(null);
		auditService.record(
			user,
			action,
			requestId,
			Map.of(
				"mode", MODE,
				"status", state.status()
			)
		);
	}

	private record ReplayState(String status, String message, Instant updatedAt) {

		private ReplayControlResponse toResponse() {
			return new ReplayControlResponse(status, MODE, message, updatedAt);
		}
	}
}
