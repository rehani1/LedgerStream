package com.ledgerstream.risk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.risk.dto.RiskHistoryResponse;
import com.ledgerstream.risk.dto.RiskSnapshotResponse;
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
class RiskControllerTest {

	private static final Instant NOW = Instant.parse("2026-01-02T14:35:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RiskQueryService riskQueryService;

	@Test
	void riskRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/portfolio/risk"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getLatestRiskReturnsSnapshot() throws Exception {
		RiskSnapshotResponse response = riskSnapshotResponse(NOW);
		when(riskQueryService.getLatestRisk(any(AuthenticatedUser.class))).thenReturn(response);

		mockMvc.perform(get("/api/portfolio/risk").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(response.id().toString()))
			.andExpect(jsonPath("$.totalEquity").value(125000.00))
			.andExpect(jsonPath("$.cash").value(100000.00))
			.andExpect(jsonPath("$.grossExposure").value(25000.00))
			.andExpect(jsonPath("$.largestPositionPct").value(20.0000))
			.andExpect(jsonPath("$.unrealizedPnl").value(750.00))
			.andExpect(jsonPath("$.createdAt").value("2026-01-02T14:35:00Z"));
	}

	@Test
	void listRiskHistoryReturnsPagedSnapshots() throws Exception {
		RiskHistoryResponse response = new RiskHistoryResponse(
			List.of(riskSnapshotResponse(NOW), riskSnapshotResponse(NOW.minusSeconds(60))),
			0,
			25,
			2,
			1
		);
		when(riskQueryService.listRiskHistory(any(AuthenticatedUser.class), eq(0), eq(25))).thenReturn(response);

		mockMvc.perform(get("/api/portfolio/risk/history")
				.param("page", "0")
				.param("size", "25")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(25))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.snapshots[0].totalEquity").value(125000.00))
			.andExpect(jsonPath("$.snapshots[0].grossExposure").value(25000.00));
	}

	private RiskSnapshotResponse riskSnapshotResponse(Instant createdAt) {
		return new RiskSnapshotResponse(
			UUID.randomUUID(),
			new BigDecimal("125000.00"),
			new BigDecimal("100000.00"),
			new BigDecimal("25000.00"),
			new BigDecimal("20.0000"),
			new BigDecimal("750.00"),
			createdAt
		);
	}

	private UsernamePasswordAuthenticationToken authenticatedUser() {
		AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), "user@example.com", UserRole.USER);
		return new UsernamePasswordAuthenticationToken(
			principal,
			"token",
			List.of(new SimpleGrantedAuthority("ROLE_USER"))
		);
	}
}
