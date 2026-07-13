package com.serphenix.portfolio.portfolio.service;

import com.serphenix.portfolio.portfolio.dto.response.HoldingResponseDto;
import com.serphenix.portfolio.portfolio.dto.response.PortfolioResponseDto;
import com.serphenix.portfolio.stock.dto.response.StockResponseDto;
import com.serphenix.portfolio.portfolio.entity.Holding;
import com.serphenix.portfolio.stock.entity.Stock;
import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.portfolio.service.PortfolioService;
import com.serphenix.portfolio.portfolio.repository.HoldingRepository;
import com.serphenix.portfolio.stock.repository.StockRepository;
import com.serphenix.portfolio.auth.repository.UserRepository;
import static org.assertj.core.api.Assertions.*;

import com.serphenix.portfolio.stock.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
        user = User.create("test@example.com", "hashed-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        stock = Stock.create("TEST");
        ReflectionTestUtils.setField(stock, "id", 10L);
    }

    @Test
    void findHoldingCalculatesCurrentValueAndUnrealizedPnlCorrectly() {

        Holding holding = Holding.create(user.getId(), stock.getId());
        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(holdingRepository.findByUserIdAndStockId(user.getId(), stock.getId())).thenReturn(Optional.of(holding));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("150"), Instant.now()
        ));

        HoldingResponseDto result = portfolioService.findHolding("test@example.com", "TEST");

        assertThat(result.currentValue()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(result.unrealizedPnl()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void findPortfolioCalculatesSumTotalValueAndUnrealizedPnlCorrectly() {

        Stock stock2 = Stock.create("TEST2");
        ReflectionTestUtils.setField(stock2, "id", 20L);

        Holding holding = Holding.create(user.getId(), stock.getId());
        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        Holding holding2 = Holding.create(user.getId(), stock2.getId());
        holding2.applyBuy(5L, new BigDecimal("200"), new BigDecimal("1000"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(holdingRepository.findByUserId(user.getId())).thenReturn(List.of(holding, holding2));
        when(stockRepository.findAllById(anyList())).thenReturn(List.of(stock, stock2));
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
