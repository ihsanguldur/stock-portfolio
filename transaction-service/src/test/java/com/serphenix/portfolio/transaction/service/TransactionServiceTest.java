package com.serphenix.portfolio.transaction.service;

import com.serphenix.portfolio.client.PortfolioClient;
import com.serphenix.portfolio.client.StockClient;
import com.serphenix.portfolio.client.WalletClient;
import com.serphenix.portfolio.client.dto.HoldingBuyRequestDto;
import com.serphenix.portfolio.client.dto.HoldingSellRequestDto;
import com.serphenix.portfolio.client.dto.StockResponseDto;
import com.serphenix.portfolio.client.dto.WalletRequestDto;
import com.serphenix.portfolio.transaction.dto.request.BuyRequestDto;
import com.serphenix.portfolio.transaction.dto.request.SellRequestDto;
import com.serphenix.portfolio.transaction.entity.Transaction;
import com.serphenix.portfolio.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private StockClient stockClient;
    @Mock
    private WalletClient walletClient;
    @Mock
    private PortfolioClient portfolioClient;

    @InjectMocks
    private TransactionService transactionService;

    private Long userId;
    private StockResponseDto stock;

    @BeforeEach
    void setUp() {
        userId = 1L;
        stock = new StockResponseDto(10L, "TEST", "Test Company", new BigDecimal("150"), Instant.now());
    }

    @Test
    void buyWithInsufficientBalancePropagatesAndDoesNotTouchPortfolio() {

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        doThrow(new RestClientException("Insufficient balance")).when(walletClient).withdraw(any());

        assertThatThrownBy(() -> transactionService.buy(userId, new BuyRequestDto("TEST", 2L)))
                .isInstanceOf(RestClientException.class);

        verify(portfolioClient, never()).applyBuy(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void buySuccessfullyWithdrawsFundsAppliesHoldingAndSavesTransaction() {

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.buy(userId, new BuyRequestDto("TEST", 2L));

        ArgumentCaptor<WalletRequestDto> walletRequestCaptor = ArgumentCaptor.forClass(WalletRequestDto.class);
        verify(walletClient).withdraw(walletRequestCaptor.capture());
        assertThat(walletRequestCaptor.getValue().amount()).isEqualByComparingTo(new BigDecimal("300"));

        ArgumentCaptor<HoldingBuyRequestDto> holdingRequestCaptor = ArgumentCaptor.forClass(HoldingBuyRequestDto.class);
        verify(portfolioClient).applyBuy(holdingRequestCaptor.capture());
        assertThat(holdingRequestCaptor.getValue().symbol()).isEqualTo("TEST");
        assertThat(holdingRequestCaptor.getValue().quantity()).isEqualTo(2L);
        assertThat(holdingRequestCaptor.getValue().price()).isEqualByComparingTo(new BigDecimal("150"));

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getQuantity()).isEqualTo(2L);
        assertThat(transactionCaptor.getValue().getPrice()).isEqualByComparingTo(new BigDecimal("150"));
    }

    @Test
    void buyCompensatesWalletWhenApplyBuyFails() {

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        doThrow(new RestClientException("portfolio-service down")).when(portfolioClient).applyBuy(any());

        assertThatThrownBy(() -> transactionService.buy(userId, new BuyRequestDto("TEST", 2L)))
                .isInstanceOf(RestClientException.class);

        verify(walletClient).withdraw(new WalletRequestDto(new BigDecimal("300")));
        verify(walletClient).deposit(new WalletRequestDto(new BigDecimal("300")));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void sellWithWalletFailurePropagatesAndDoesNotTouchPortfolio() {

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        doThrow(new RestClientException("wallet-service down")).when(walletClient).deposit(any());

        assertThatThrownBy(() -> transactionService.sell(userId, new SellRequestDto("TEST", 2L)))
                .isInstanceOf(RestClientException.class);

        verify(portfolioClient, never()).applySell(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void sellSuccessfullyDepositsFundsAppliesHoldingAndSavesTransaction() {

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.sell(userId, new SellRequestDto("TEST", 2L));

        verify(walletClient).deposit(new WalletRequestDto(new BigDecimal("300")));
        verify(portfolioClient).applySell(new HoldingSellRequestDto("TEST", 2L));

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getQuantity()).isEqualTo(2L);
        assertThat(transactionCaptor.getValue().getPrice()).isEqualByComparingTo(new BigDecimal("150"));
    }

    @Test
    void sellCompensatesDepositWhenApplySellFails() {

        when(stockClient.getBySymbol("TEST")).thenReturn(stock);
        doThrow(new RestClientException("Insufficient holding")).when(portfolioClient).applySell(any());

        assertThatThrownBy(() -> transactionService.sell(userId, new SellRequestDto("TEST", 2L)))
                .isInstanceOf(RestClientException.class);

        verify(walletClient).deposit(new WalletRequestDto(new BigDecimal("300")));
        verify(walletClient).withdraw(new WalletRequestDto(new BigDecimal("300")));
        verify(transactionRepository, never()).save(any());
    }
}
