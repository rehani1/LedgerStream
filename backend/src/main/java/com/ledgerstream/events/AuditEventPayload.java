package com.ledgerstream.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventPayload(
	UUID eventId,
	UUID userId,
	String action,
	String requestId,
	Map<String, Object> metadata,
	Instant createdAt
) {
}
