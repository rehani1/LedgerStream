package com.ledgerstream.admin;

import java.time.Instant;
import java.util.Map;

import com.ledgerstream.events.EventTopics;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

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
			)
		);
	}

	public record QueueHealthResponse(
		String status,
		Instant checkedAt,
		Map<String, String> topics
	) {
	}
}
