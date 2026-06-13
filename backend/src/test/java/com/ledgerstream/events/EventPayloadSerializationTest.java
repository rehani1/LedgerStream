package com.ledgerstream.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class EventPayloadSerializationTest {

	@Test
	void marketTickEventSerializesWithStableJsonFieldNames() throws Exception {
		UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		MarketTickEvent event = new MarketTickEvent(
			eventId,
			"AAPL",
			Instant.parse("2026-01-01T14:30:00Z"),
			new BigDecimal("187.120000"),
			new BigDecimal("187.180000"),
			new BigDecimal("187.150000"),
			1000L,
			"fixture"
		);

		String json = JsonMapper.builder()
			.findAndAddModules()
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.build()
			.writeValueAsString(event);

		assertThat(json).contains("\"eventId\":\"" + eventId + "\"");
		assertThat(json).contains("\"symbol\":\"AAPL\"");
		assertThat(json).contains("\"timestamp\":\"2026-01-01T14:30:00Z\"");
		assertThat(json).contains("\"last\":187.150000");
		assertThat(json).contains("\"source\":\"fixture\"");
	}
}
