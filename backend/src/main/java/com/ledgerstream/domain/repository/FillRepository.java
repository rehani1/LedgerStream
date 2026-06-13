package com.ledgerstream.domain.repository;

import java.util.List;
import java.util.UUID;

import com.ledgerstream.domain.model.Fill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FillRepository extends JpaRepository<Fill, UUID> {

	List<Fill> findByOrderId(UUID orderId);
}
