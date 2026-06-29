package com.ledgerstream.admin;

import java.time.Instant;
import java.util.Map;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.config.properties.KafkaProperties;
import com.ledgerstream.events.EventTopics;
import com.ledgerstream.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final ReplayControlService replayControlService;
	private final KafkaProperties kafkaProperties;

	public AdminController(ReplayControlService replayControlService, KafkaProperties kafkaProperties) {
		this.replayControlService = replayControlService;
		this.kafkaProperties = kafkaProperties;
	}

	@GetMapping("/market/replay/status")
	public ReplayControlResponse replayStatus() {
		return replayControlService.currentStatus();
	}

	@PostMapping("/market/replay/start")
	public ReplayControlResponse startReplay(
		@AuthenticationPrincipal AuthenticatedUser admin,
		HttpServletRequest request
	) {
		return replayControlService.start(admin, requestId(request));
	}

	@PostMapping("/market/replay/stop")
	public ReplayControlResponse stopReplay(
		@AuthenticationPrincipal AuthenticatedUser admin,
		HttpServletRequest request
	) {
		return replayControlService.stop(admin, requestId(request));
	}

	@GetMapping("/queue-health")
	public QueueHealthResponse queueHealth() {
		return new QueueHealthResponse(
			"topics_configured",
			Instant.now(),
			Map.of(
				"marketTick", EventTopics.MARKET_TICK,
				"orderCreated", EventTopics.ORDER_CREATED,
				"orderFilled", EventTopics.ORDER_FILLED,
				"portfolioUpdated", EventTopics.PORTFOLIO_UPDATED,
				"riskUpdated", EventTopics.RISK_UPDATED,
				"auditEvent", EventTopics.AUDIT_EVENT
			),
			Map.of(
				"marketTick", EventTopics.deadLetterTopic(EventTopics.MARKET_TICK, kafkaProperties.deadLetterSuffix()),
				"orderCreated", EventTopics.deadLetterTopic(EventTopics.ORDER_CREATED, kafkaProperties.deadLetterSuffix())
			),
			new QueueRetryPolicyResponse(
				kafkaProperties.retryMaxAttempts(),
				kafkaProperties.retryBackoff().toString(),
				kafkaProperties.deadLetterSuffix()
			)
		);
	}

	public record QueueHealthResponse(
		String status,
		Instant checkedAt,
		Map<String, String> topics,
		Map<String, String> deadLetterTopics,
		QueueRetryPolicyResponse retryPolicy
	) {
	}

	public record QueueRetryPolicyResponse(
		long retryMaxAttempts,
		String retryBackoff,
		String deadLetterSuffix
	) {
	}

	private String requestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return requestId == null ? null : requestId.toString();
	}
}
