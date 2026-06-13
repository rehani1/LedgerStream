package com.ledgerstream.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

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
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void meReturnsCurrentAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		AuthenticatedUser principal = new AuthenticatedUser(userId, "me@example.com", UserRole.USER);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
			principal,
			"token",
			List.of(new SimpleGrantedAuthority("ROLE_USER"))
		);

		mockMvc.perform(get("/api/me").with(authentication(auth)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId.toString()))
			.andExpect(jsonPath("$.email").value("me@example.com"))
			.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void meRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.requestId").exists());
	}
}
