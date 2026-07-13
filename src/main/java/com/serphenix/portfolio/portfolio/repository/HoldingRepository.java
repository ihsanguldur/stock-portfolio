package com.serphenix.portfolio.portfolio.repository;

import com.serphenix.portfolio.portfolio.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {
    Optional<Holding> findByUserIdAndStockId(Long userId, Long stockId);

    List<Holding> findByUserId(Long userId);

    @Query("SELECT DISTINCT h.stockId FROM Holding h")
    List<Long> findDistinctStockIds();
}
