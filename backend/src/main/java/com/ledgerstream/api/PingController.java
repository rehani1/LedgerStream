package com.ledgerstream.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

	@GetMapping("/api/ping")
	public PingResponse ping() {
		return new PingResponse("ledgerstream-backend", "ok", Instant.now());
	}

	public record PingResponse(String service, String status, Instant timestamp) {
	}
}
