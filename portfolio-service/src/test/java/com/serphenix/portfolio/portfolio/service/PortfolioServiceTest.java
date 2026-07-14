package com.serphenix.portfolio.portfolio.service;

import com.serphenix.portfolio.client.StockClient;
import com.serphenix.portfolio.client.dto.StockResponseDto;
import com.serphenix.portfolio.portfolio.dto.request.HoldingBuyRequestDto;
import com.serphenix.portfolio.portfolio.dto.request.HoldingSellRequestDto;
import com.serphenix.portfolio.portfolio.dto.response.HoldingResponseDto;
import com.serphenix.portfolio.portfolio.dto.response.PortfolioResponseDto;
import com.serphenix.portfolio.portfolio.entity.Holding;
import com.serphenix.portfolio.portfolio.exception.HoldingNotFoundException;
import com.serphenix.portfolio.portfolio.exception.InsufficientHoldingException;
import com.serphenix.portfolio.portfolio.repository.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PortfolioServiceTest {

    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private StockClient stockClient;

    @InjectMocks
    private PortfolioService portfolioService;

    private Long userId;
    private StockResponseDto stock;

    @BeforeEach
    void setUp() {
        userId = 1L;
        stock = new StockResponseDto(10L, "TEST", "Test Company", new BigDecimal("150"), Instant.now());
    }

    @Test
    void findHoldingCalculatesCurrentValueAndUnrealizedPnlCorrectly() {

        Holding holding = Holding.create(userId, stock.id());
        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(holdingRepository.findByUserIdAndStockId(userId, stock.id())).thenReturn(Optional.of(holding));

        HoldingResponseDto result = portfolioService.findHolding(userId, "TEST");

        assertThat(result.currentValue()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(result.unrealizedPnl()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void findHoldingThrowsWhenHoldingNotFound() {
        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(holdingRepository.findByUserIdAndStockId(userId, stock.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.findHolding(userId, "TEST"))
                .isInstanceOf(HoldingNotFoundException.class);
    }

    @Test
    void findPortfolioCalculatesSumTotalValueAndUnrealizedPnlCorrectly() {

        StockResponseDto stock2 = new StockResponseDto(20L, "TEST2", "Test 2 Company", new BigDecimal("180"), Instant.now());

        Holding holding = Holding.create(userId, stock.id());
        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        Holding holding2 = Holding.create(userId, stock2.id());
        holding2.applyBuy(5L, new BigDecimal("200"), new BigDecimal("1000"));

        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(holding, holding2));
        when(stockClient.getByIds(anyList())).thenReturn(List.of(stock, stock2));

        PortfolioResponseDto result = portfolioService.findPortfolio(userId);

        assertThat(result.holdings()).hasSize(2);
        assertThat(result.totalValue()).isEqualByComparingTo(new BigDecimal("2400"));
        assertThat(result.totalUnrealizedPnl()).isEqualByComparingTo(new BigDecimal("400"));
    }

    @Test
    void applyBuyFirstTimeCreatesNewHoldingWithCorrectQuantityAndAvgCost() {
        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(holdingRepository.findByUserIdAndStockId(userId, stock.id())).thenReturn(Optional.empty());

        portfolioService.applyBuy(userId, new HoldingBuyRequestDto("TEST", 10L, new BigDecimal("100")));

        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());

        Holding savedHolding = holdingCaptor.getValue();
        assertThat(savedHolding.getQuantity()).isEqualTo(10L);
        assertThat(savedHolding.getAvgCost()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void applyBuySecondTimeUpdatesHoldingAndRecalculatesAvgCost() {
        Holding holding = Holding.create(userId, stock.id());
        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(holdingRepository.findByUserIdAndStockId(userId, stock.id())).thenReturn(Optional.of(holding));

        portfolioService.applyBuy(userId, new HoldingBuyRequestDto("TEST", 5L, new BigDecimal("150")));

        assertThat(holding.getQuantity()).isEqualTo(15L);
        assertThat(holding.getAvgCost()).isEqualByComparingTo(new BigDecimal("116.6667"));
        verify(holdingRepository).save(holding);
    }

    @Test
    void applySellReducesQuantityAndKeepsHoldingWhenNotEmpty() {
        Holding holding = Holding.create(userId, stock.id());
        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(holdingRepository.findByUserIdAndStockId(userId, stock.id())).thenReturn(Optional.of(holding));

        portfolioService.applySell(userId, new HoldingSellRequestDto("TEST", 5L));

        assertThat(holding.getQuantity()).isEqualTo(5L);
        assertThat(holding.getAvgCost()).isEqualByComparingTo(new BigDecimal("100"));
        verify(holdingRepository).save(holding);
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void applySellDeletesHoldingWhenQuantityReachesZero() {
        Holding holding = Holding.create(userId, stock.id());
        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(holdingRepository.findByUserIdAndStockId(userId, stock.id())).thenReturn(Optional.of(holding));

        portfolioService.applySell(userId, new HoldingSellRequestDto("TEST", 10L));

        assertThat(holding.getQuantity()).isEqualTo(0L);
        verify(holdingRepository).delete(holding);
        verify(holdingRepository, never()).save(any());
    }

    @Test
    void applySellThrowsWhenHoldingNotFound() {
        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(holdingRepository.findByUserIdAndStockId(userId, stock.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                portfolioService.applySell(userId, new HoldingSellRequestDto("TEST", 5L))
        ).isInstanceOf(HoldingNotFoundException.class);

        verify(holdingRepository, never()).save(any());
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void applySellThrowsWhenQuantityExceedsHolding() {
        Holding holding = Holding.create(userId, stock.id());
        holding.applyBuy(3L, new BigDecimal("100"), new BigDecimal("300"));

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(holdingRepository.findByUserIdAndStockId(userId, stock.id())).thenReturn(Optional.of(holding));

        assertThatThrownBy(() ->
                portfolioService.applySell(userId, new HoldingSellRequestDto("TEST", 5L))
        ).isInstanceOf(InsufficientHoldingException.class);

        verify(holdingRepository, never()).save(any());
        verify(holdingRepository, never()).delete(any());
    }
}
