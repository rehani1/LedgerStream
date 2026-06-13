package com.ledgerstream.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.RiskSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskSnapshotRepository extends JpaRepository<RiskSnapshot, UUID> {

	Optional<RiskSnapshot> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

	Page<RiskSnapshot> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
