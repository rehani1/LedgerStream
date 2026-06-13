package com.ledgerstream.auth;

import java.util.UUID;

import com.ledgerstream.domain.model.UserRole;

public record AuthenticatedUser(UUID id, String email, UserRole role) {
}
