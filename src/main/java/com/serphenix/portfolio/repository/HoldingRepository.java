package com.serphenix.portfolio.repository;

import com.serphenix.portfolio.entity.Holding;
import com.serphenix.portfolio.entity.Stock;
import com.serphenix.portfolio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {
    Optional<Holding> findByUserAndStock(User user, Stock stock);

    @Query("SELECT DISTINCT h.stock FROM Holding h")
    List<Stock> findDistinctStocks();
}
