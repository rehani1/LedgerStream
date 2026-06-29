package com.ledgerstream.domain.repository;

import java.util.UUID;

import com.ledgerstream.domain.model.PortfolioSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

	Page<PortfolioSnapshot> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
