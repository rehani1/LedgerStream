package com.ledgerstream.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ledgerstream.web.RequestIdFilter;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class PingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void pingReturnsBackendStatus() throws Exception {
		mockMvc.perform(get("/api/ping").header(RequestIdFilter.REQUEST_ID_HEADER, "ping-test-1"))
			.andExpect(status().isOk())
			.andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "ping-test-1"))
			.andExpect(jsonPath("$.service").value("ledgerstream-backend"))
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.timestamp", notNullValue()));
	}

	@Test
	void healthEndpointIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void prometheusEndpointIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/prometheus"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("# HELP")));
	}
}
