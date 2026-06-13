package com.ledgerstream.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.OrderStatus;
import com.ledgerstream.domain.model.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<TradeOrder, UUID> {

	List<TradeOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);

	Optional<TradeOrder> findByIdAndUserId(UUID id, UUID userId);

	Optional<TradeOrder> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

	List<TradeOrder> findBySymbolTickerAndStatus(String ticker, OrderStatus status);
}
