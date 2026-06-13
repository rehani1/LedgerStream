package com.ledgerstream.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
	@NotBlank @Email String email,
	@NotBlank
	@Pattern(
		regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,128}$",
		message = "must be 8 to 128 characters and include at least one letter and one number"
	)
	String password
) {
}
