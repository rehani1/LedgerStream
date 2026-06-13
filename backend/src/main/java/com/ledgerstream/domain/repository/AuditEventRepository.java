package com.ledgerstream.domain.repository;

import java.util.UUID;

import com.ledgerstream.domain.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

	Page<AuditEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	Page<AuditEvent> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}
