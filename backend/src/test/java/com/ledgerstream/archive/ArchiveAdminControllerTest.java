package com.ledgerstream.archive;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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
class ArchiveAdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ArchiveService archiveService;

	@Test
	void exportPortfolioSnapshotsReturnsArchiveLocation() throws Exception {
		ArchiveExportResponse response = new ArchiveExportResponse(
			"portfolio_snapshots",
			"portfolio-snapshots/date=2026-01-02/archive.json",
			"file:///tmp/archive.json",
			128,
			"8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4",
			2,
			Instant.parse("2026-01-03T01:00:00Z")
		);
		when(archiveService.exportPortfolioSnapshots(eq(LocalDate.parse("2026-01-02")))).thenReturn(response);

		mockMvc.perform(post("/api/admin/archive/portfolio-snapshots")
				.param("date", "2026-01-02")
				.with(authentication(adminUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archiveType").value("portfolio_snapshots"))
			.andExpect(jsonPath("$.key").value("portfolio-snapshots/date=2026-01-02/archive.json"))
			.andExpect(jsonPath("$.uri").value("file:///tmp/archive.json"))
			.andExpect(jsonPath("$.sizeBytes").value(128))
			.andExpect(jsonPath("$.exportedRecords").value(2))
			.andExpect(jsonPath("$.checksumSha256").value(response.checksumSha256()));
	}

	private UsernamePasswordAuthenticationToken adminUser() {
		AuthenticatedUser principal = new AuthenticatedUser(
			UUID.fromString("00000000-0000-0000-0000-000000000901"),
			"admin@example.com",
			UserRole.ADMIN
		);
		return new UsernamePasswordAuthenticationToken(
			principal,
			"token",
			List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
		);
	}
}
