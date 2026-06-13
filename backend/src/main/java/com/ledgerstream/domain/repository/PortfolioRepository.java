package com.ledgerstream.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

	Optional<Portfolio> findByUserId(UUID userId);
}
