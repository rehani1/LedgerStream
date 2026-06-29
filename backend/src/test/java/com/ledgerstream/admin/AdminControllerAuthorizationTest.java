package com.ledgerstream.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerAuthorizationTest {

	private static final Instant UPDATED_AT = Instant.parse("2026-01-02T14:30:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReplayControlService replayControlService;

	@Test
	void queueHealthRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/admin/queue-health"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void queueHealthRejectsNormalUser() throws Exception {
		mockMvc.perform(get("/api/admin/queue-health").with(authentication(authenticatedUser(UserRole.USER))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void queueHealthAllowsAdminUser() throws Exception {
		mockMvc.perform(get("/api/admin/queue-health").with(authentication(authenticatedUser(UserRole.ADMIN))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("topics_configured"))
			.andExpect(jsonPath("$.checkedAt").exists())
			.andExpect(jsonPath("$.topics.marketTick").value("market.tick"))
			.andExpect(jsonPath("$.deadLetterTopics.marketTick").value("market.tick.DLT"))
			.andExpect(jsonPath("$.deadLetterTopics.orderCreated").value("order.created.DLT"))
			.andExpect(jsonPath("$.retryPolicy.retryMaxAttempts").value(3))
			.andExpect(jsonPath("$.retryPolicy.retryBackoff").value("PT2S"))
			.andExpect(jsonPath("$.retryPolicy.deadLetterSuffix").value(".DLT"));
	}

	@Test
	void replayStatusRejectsNormalUser() throws Exception {
		mockMvc.perform(get("/api/admin/market/replay/status").with(authentication(authenticatedUser(UserRole.USER))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void replayStatusAllowsAdminUser() throws Exception {
		when(replayControlService.currentStatus()).thenReturn(response("STOPPED"));

		mockMvc.perform(get("/api/admin/market/replay/status").with(authentication(authenticatedUser(UserRole.ADMIN))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("STOPPED"))
			.andExpect(jsonPath("$.mode").value("backend_state"))
			.andExpect(jsonPath("$.updatedAt").exists());
	}

	@Test
	void startReplayRequiresAdmin() throws Exception {
		mockMvc.perform(post("/api/admin/market/replay/start").with(authentication(authenticatedUser(UserRole.USER))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void startReplayAllowsAdminUser() throws Exception {
		when(replayControlService.start(any(AuthenticatedUser.class), eq("admin-start-1"))).thenReturn(response("RUNNING"));

		mockMvc.perform(post("/api/admin/market/replay/start")
				.header("X-Request-ID", "admin-start-1")
				.with(authentication(authenticatedUser(UserRole.ADMIN))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RUNNING"))
			.andExpect(jsonPath("$.mode").value("backend_state"));
	}

	@Test
	void stopReplayAllowsAdminUser() throws Exception {
		when(replayControlService.stop(any(AuthenticatedUser.class), eq("admin-stop-1"))).thenReturn(response("STOPPED"));

		mockMvc.perform(post("/api/admin/market/replay/stop")
				.header("X-Request-ID", "admin-stop-1")
				.with(authentication(authenticatedUser(UserRole.ADMIN))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("STOPPED"))
			.andExpect(jsonPath("$.mode").value("backend_state"));
	}

	private UsernamePasswordAuthenticationToken authenticatedUser(UserRole role) {
		AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), role.name().toLowerCase() + "@example.com", role);
		return new UsernamePasswordAuthenticationToken(
			principal,
			"token",
			List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
		);
	}

	private ReplayControlResponse response(String status) {
		return new ReplayControlResponse(status, "backend_state", "Replay state is " + status.toLowerCase() + ".", UPDATED_AT);
	}
}
