package com.ledgerstream.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.PriceTick;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceTickRepository extends JpaRepository<PriceTick, Long> {

	List<PriceTick> findTop500BySymbolTickerAndTsAfterOrderByTsAsc(String ticker, Instant after);

	Optional<PriceTick> findFirstBySymbolTickerOrderByTsDesc(String ticker);

	boolean existsBySymbolIdAndTsAndSource(UUID symbolId, Instant ts, String source);
}
