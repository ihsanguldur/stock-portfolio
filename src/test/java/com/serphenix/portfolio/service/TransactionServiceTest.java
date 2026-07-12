package com.serphenix.portfolio.service;

import com.serphenix.portfolio.dto.request.BuyRequestDto;
import com.serphenix.portfolio.dto.request.SellRequestDto;
import com.serphenix.portfolio.dto.response.StockResponseDto;
import com.serphenix.portfolio.entity.*;
import com.serphenix.portfolio.exception.InsufficientBalanceException;
import com.serphenix.portfolio.exception.InsufficientHoldingException;
import com.serphenix.portfolio.repository.*;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private StockRepository stockRepository;
    @Mock private StockService stockService;
    @Mock private WalletRepository walletRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private TransactionService transactionService;

    private User user;
    private Stock stock;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("test@example.com");

        stock = new Stock();
        stock.setSymbol("TEST");

        wallet = new Wallet();
        wallet.setBalance(new BigDecimal("1000"));
    }

    @Test
    void buyWithInsufficientBalanceThrowsAndDoesNotTouchWallet() {

        wallet.setBalance(new BigDecimal("50"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("100"), Instant.now()
        ));
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> transactionService.buy("test@example.com", new BuyRequestDto(
                "TEST", 2L
        ))).isInstanceOf(InsufficientBalanceException.class);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void buyFirstTimeCreatesNewHoldingWithCorrectQuantityAndAvgCost() {

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("100"), Instant.now()
        ));
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(holdingRepository.findByUserAndStock(user, stock)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.buy("test@example.com", new BuyRequestDto("TEST", 2L));

        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());

        Holding savedHolding = holdingCaptor.getValue();
        assertThat(savedHolding.getQuantity()).isEqualTo(2L);
        assertThat(savedHolding.getAvgCost()).isEqualByComparingTo(new BigDecimal("100"));

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("800"));
    }

    @Test
    void buySecondTimeUpdateHoldingAndRecalculatesAvgCost() {

        Holding holding = new Holding();
        holding.setUser(user);
        holding.setStock(stock);
        holding.setQuantity(10L);
        holding.setAvgCost(new BigDecimal("100"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("150"), Instant.now()
        ));
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(holdingRepository.findByUserAndStock(user, stock)).thenReturn(Optional.of(holding));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.buy("test@example.com", new BuyRequestDto("TEST", 5L));

        assertThat(holding.getQuantity()).isEqualTo(15L);
        assertThat(holding.getAvgCost()).isEqualByComparingTo(new BigDecimal("116.6667"));

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("250"));
    }

    @Test
    void sellWithInsufficientHoldingQuantityThrowsAndDoesNotTouchWallet() {

        Holding holding = new Holding();
        holding.setUser(user);
        holding.setStock(stock);
        holding.setQuantity(3L);
        holding.setAvgCost(new BigDecimal("100"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(holdingRepository.findByUserAndStock(user, stock)).thenReturn(Optional.of(holding));

        assertThatThrownBy(() ->
                transactionService.sell("test@example.com", new SellRequestDto("TEST", 5L))
        ).isInstanceOf(InsufficientHoldingException.class);

        verify(holdingRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void sellValidQuantityUpdatesQuantityWalletAndKeepsAvgCostUnchanged() {

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
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.sell("test@example.com", new SellRequestDto("TEST", 5L));

        assertThat(holding.getQuantity()).isEqualTo(5L);
        assertThat(holding.getAvgCost()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("1750"));

        verify(holdingRepository).save(holding);
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void sellDeleteHoldingIfQuantityIsZero() {

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
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.sell("test@example.com", new SellRequestDto("TEST", 10L));

        assertThat(holding.getQuantity()).isEqualTo(0L);
        assertThat(holding.getAvgCost()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("2500"));

        verify(holdingRepository).delete(holding);
        verify(holdingRepository, never()).save(any());
    }
}
