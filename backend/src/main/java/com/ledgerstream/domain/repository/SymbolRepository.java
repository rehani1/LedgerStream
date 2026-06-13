package com.ledgerstream.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.domain.model.Symbol;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SymbolRepository extends JpaRepository<Symbol, UUID> {

	Optional<Symbol> findByTicker(String ticker);

	List<Symbol> findByActiveTrueOrderByTickerAsc();
}
