package com.ledgerstream.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.ErrorTestController.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void responseStatusExceptionsUseStandardErrorShape() throws Exception {
		mockMvc.perform(get("/api/test-errors/bad-request")
				.with(user("test-user"))
				.header(RequestIdFilter.REQUEST_ID_HEADER, "request-123"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("Bad Request"))
			.andExpect(jsonPath("$.message").value("Bad input"))
			.andExpect(jsonPath("$.path").value("/api/test-errors/bad-request"))
			.andExpect(jsonPath("$.requestId").value("request-123"));
	}

	@Test
	void validationErrorsUseStandardErrorShape() throws Exception {
		mockMvc.perform(post("/api/test-errors/validated")
				.with(user("test-user"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message", containsString("name")))
			.andExpect(jsonPath("$.requestId").exists());
	}

	@RestController
	@RequestMapping("/api/test-errors")
	static class ErrorTestController {

		@GetMapping("/bad-request")
		void badRequest() {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad input");
		}

		@PostMapping("/validated")
		void validated(@Valid @RequestBody ValidationRequest request) {
		}
	}

	record ValidationRequest(@NotBlank String name) {
	}
}
