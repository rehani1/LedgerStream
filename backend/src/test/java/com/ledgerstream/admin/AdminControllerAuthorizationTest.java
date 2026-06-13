package com.ledgerstream.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

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
			.andExpect(jsonPath("$.status").value("not_configured"))
			.andExpect(jsonPath("$.checkedAt").exists());
	}

	private UsernamePasswordAuthenticationToken authenticatedUser(UserRole role) {
		AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), role.name().toLowerCase() + "@example.com", role);
		return new UsernamePasswordAuthenticationToken(
			principal,
			"token",
			List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
		);
	}
}
