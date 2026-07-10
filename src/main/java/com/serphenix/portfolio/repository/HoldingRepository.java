package com.serphenix.portfolio.repository;

import com.serphenix.portfolio.entity.Holding;
import com.serphenix.portfolio.entity.Stock;
import com.serphenix.portfolio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {
    Optional<Holding> findByUserAndStock(User user, Stock stock);

    List<Holding> findByUser(User user);
}
