package com.ledgerstream.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.CashTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashTransferRepository extends JpaRepository<CashTransfer, UUID> {

	Optional<CashTransfer> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
