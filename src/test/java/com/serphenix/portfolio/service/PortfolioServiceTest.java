package com.serphenix.portfolio.service;

import com.serphenix.portfolio.dto.response.HoldingResponseDto;
import com.serphenix.portfolio.dto.response.PortfolioResponseDto;
import com.serphenix.portfolio.dto.response.StockResponseDto;
import com.serphenix.portfolio.entity.Holding;
import com.serphenix.portfolio.entity.Stock;
import com.serphenix.portfolio.entity.User;
import com.serphenix.portfolio.entity.Wallet;
import com.serphenix.portfolio.repository.HoldingRepository;
import com.serphenix.portfolio.repository.StockRepository;
import com.serphenix.portfolio.repository.UserRepository;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class PortfolioServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private StockRepository stockRepository;
    @Mock private StockService stockService;

    @InjectMocks private PortfolioService portfolioService;

    private User user;
    private Stock stock;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("test@example.com");

        stock = new Stock();
        stock.setSymbol("TEST");
    }

    @Test
    void findHoldingCalculatesCurrentValueAndUnrealizedPnlCorrectly() {

        Holding holding = new Holding();
        holding.setUser(user);
        holding.setStock(stock);
        holding.setQuantity(10L);
        holding.setAvgCost(new BigDecimal("100"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(holdingRepository.findByUserAndStock(user, stock)).thenReturn(Optional.of(holding));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("150"), Instant.now()
        ));

        HoldingResponseDto result = portfolioService.findHolding("test@example.com", "TEST");

        assertThat(result.currentValue()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(result.unrealizedPnl()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void findPortfolioCalculatesSumTotalValueAndUnrealizedPnlCorrectly() {

        Stock stock2 = new Stock();
        stock2.setSymbol("TEST2");

        Holding holding = new Holding();
        holding.setUser(user);
        holding.setStock(stock);
        holding.setQuantity(10L);
        holding.setAvgCost(new BigDecimal("100"));

        Holding holding2 = new Holding();
        holding2.setUser(user);
        holding2.setStock(stock2);
        holding2.setQuantity(5L);
        holding2.setAvgCost(new BigDecimal("200"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(holdingRepository.findByUser(user)).thenReturn(List.of(holding, holding2));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("150"), Instant.now()
        ));
        when(stockService.getPrice("TEST2")).thenReturn(new StockResponseDto(
                2L, "TEST2", "Test 2 Company", new BigDecimal("180"), Instant.now()
        ));

        PortfolioResponseDto result = portfolioService.findPortfolio("test@example.com");

        assertThat(result.holdings()).hasSize(2);
        assertThat(result.totalValue()).isEqualByComparingTo(new BigDecimal("2400"));
        assertThat(result.totalUnrealizedPnl()).isEqualByComparingTo(new BigDecimal("400"));
    }
}
