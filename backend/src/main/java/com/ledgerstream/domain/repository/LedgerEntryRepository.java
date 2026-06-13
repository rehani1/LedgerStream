package com.ledgerstream.domain.repository;

import java.util.UUID;

import com.ledgerstream.domain.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	Page<LedgerEntry> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
