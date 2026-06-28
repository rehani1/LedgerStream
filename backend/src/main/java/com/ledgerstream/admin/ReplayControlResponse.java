package com.ledgerstream.admin;

import java.time.Instant;

public record ReplayControlResponse(
	String status,
	String mode,
	String message,
	Instant updatedAt
) {
}
