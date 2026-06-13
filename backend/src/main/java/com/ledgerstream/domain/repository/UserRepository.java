package com.ledgerstream.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
