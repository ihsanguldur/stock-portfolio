package com.serphenix.portfolio.transaction.service;

import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.auth.repository.UserRepository;
import com.serphenix.portfolio.stock.entity.Stock;
import com.serphenix.portfolio.stock.repository.StockRepository;
import com.serphenix.portfolio.stock.service.StockService;
import com.serphenix.portfolio.transaction.dto.request.BuyRequestDto;
import com.serphenix.portfolio.transaction.dto.request.SellRequestDto;
import com.serphenix.portfolio.stock.dto.response.StockResponseDto;
import com.serphenix.portfolio.portfolio.entity.Holding;
import com.serphenix.portfolio.portfolio.repository.HoldingRepository;
import com.serphenix.portfolio.transaction.entity.Transaction;
import com.serphenix.portfolio.transaction.repository.TransactionRepository;
import com.serphenix.portfolio.transaction.service.TransactionService;
import com.serphenix.portfolio.wallet.exception.InsufficientBalanceException;
import com.serphenix.portfolio.portfolio.exception.InsufficientHoldingException;

import static org.assertj.core.api.Assertions.*;

import com.serphenix.portfolio.wallet.entity.Wallet;
import com.serphenix.portfolio.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private TransactionService transactionService;

    private User user;
    private Stock stock;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = User.create("test@example.com", "hashed-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        stock = Stock.create("TEST");
        ReflectionTestUtils.setField(stock, "id", 10L);

        wallet = Wallet.create(user.getId(), new BigDecimal("1000"));
    }

    @Test
    void buyWithInsufficientBalanceThrowsAndDoesNotTouchWallet() {

        wallet = Wallet.create(user.getId(), new BigDecimal("50"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("100"), Instant.now()
        ));
        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));

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
        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
        when(holdingRepository.findByUserIdAndStockId(user.getId(), stock.getId())).thenReturn(Optional.empty());
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

        Holding holding = Holding.create(user.getId(), stock.getId());

        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("150"), Instant.now()
        ));
        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
        when(holdingRepository.findByUserIdAndStockId(user.getId(), stock.getId())).thenReturn(Optional.of(holding));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.buy("test@example.com", new BuyRequestDto("TEST", 5L));

        assertThat(holding.getQuantity()).isEqualTo(15L);
        assertThat(holding.getAvgCost()).isEqualByComparingTo(new BigDecimal("116.6667"));

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("250"));
    }

    @Test
    void sellWithInsufficientHoldingQuantityThrowsAndDoesNotTouchWallet() {

        Holding holding = Holding.create(user.getId(), stock.getId());
        holding.applyBuy(3L, new BigDecimal("100"), new BigDecimal("300"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(holdingRepository.findByUserIdAndStockId(user.getId(), stock.getId())).thenReturn(Optional.of(holding));

        assertThatThrownBy(() ->
                transactionService.sell("test@example.com", new SellRequestDto("TEST", 5L))
        ).isInstanceOf(InsufficientHoldingException.class);

        verify(holdingRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void sellValidQuantityUpdatesQuantityWalletAndKeepsAvgCostUnchanged() {

        Holding holding = Holding.create(user.getId(), stock.getId());
        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(holdingRepository.findByUserIdAndStockId(user.getId(), stock.getId())).thenReturn(Optional.of(holding));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("150"), Instant.now()
        ));
        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
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

        Holding holding = Holding.create(user.getId(), stock.getId());
        holding.applyBuy(10L, new BigDecimal("100"), new BigDecimal("1000"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("TEST")).thenReturn(Optional.of(stock));
        when(holdingRepository.findByUserIdAndStockId(user.getId(), stock.getId())).thenReturn(Optional.of(holding));
        when(stockService.getPrice("TEST")).thenReturn(new StockResponseDto(
                1L, "TEST", "Test Company", new BigDecimal("150"), Instant.now()
        ));
        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.sell("test@example.com", new SellRequestDto("TEST", 10L));

        assertThat(holding.getQuantity()).isEqualTo(0L);
        assertThat(holding.getAvgCost()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("2500"));

        verify(holdingRepository).delete(holding);
        verify(holdingRepository, never()).save(any());
    }
}
