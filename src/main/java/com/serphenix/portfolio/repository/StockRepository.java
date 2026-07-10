package com.serphenix.portfolio.repository;

import com.serphenix.portfolio.entity.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySymbol(String symbol);

    @Query("SELECT s FROM Stock s " +
            "WHERE LOWER(s.symbol) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Stock> search(@Param("search") String search, Pageable pageable);
}