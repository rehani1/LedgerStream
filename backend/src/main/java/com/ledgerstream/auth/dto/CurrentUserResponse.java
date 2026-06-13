package com.ledgerstream.auth.dto;

import java.util.UUID;

import com.ledgerstream.domain.model.UserRole;

public record CurrentUserResponse(UUID id, String email, UserRole role) {
}
