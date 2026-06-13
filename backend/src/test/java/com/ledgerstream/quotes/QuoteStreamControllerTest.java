package com.ledgerstream.quotes;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class QuoteStreamControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private QuoteStreamService quoteStreamService;

	@Test
	void quoteStreamRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/stream/quotes").param("symbols", "AAPL"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void quoteStreamStartsSseEmitterForAuthenticatedUser() throws Exception {
		SseEmitter emitter = new SseEmitter(1_000L);
		when(quoteStreamService.openStream("AAPL,MSFT")).thenReturn(emitter);

		mockMvc.perform(get("/api/stream/quotes")
				.param("symbols", "AAPL,MSFT")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(request().asyncStarted());

		emitter.complete();
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
