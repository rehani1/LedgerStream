package com.ledgerstream.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, UUID> {

	List<Position> findByUserId(UUID userId);

	Optional<Position> findByUserIdAndSymbolTicker(UUID userId, String ticker);

	boolean existsByIdAndUserId(UUID id, UUID userId);
}
