package com.ledgerstream.admin;

import java.time.Instant;
import java.util.Map;

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
		return new QueueHealthResponse("not_configured", Instant.now(), Map.of());
	}

	public record QueueHealthResponse(
		String status,
		Instant checkedAt,
		Map<String, String> topics
	) {
	}
}
