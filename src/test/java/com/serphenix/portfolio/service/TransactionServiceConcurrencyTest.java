package com.serphenix.portfolio.service;

import com.serphenix.portfolio.config.RedisConfig;
import com.serphenix.portfolio.dto.request.BuyRequestDto;
import com.serphenix.portfolio.entity.Holding;
import com.serphenix.portfolio.entity.Stock;
import com.serphenix.portfolio.entity.User;
import com.serphenix.portfolio.entity.Wallet;
import com.serphenix.portfolio.entity.enums.Role;
import com.serphenix.portfolio.external.StockPriceClient;
import com.serphenix.portfolio.helper.RetryHelper;
import com.serphenix.portfolio.repository.HoldingRepository;
import com.serphenix.portfolio.repository.StockRepository;
import com.serphenix.portfolio.repository.UserRepository;
import com.serphenix.portfolio.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class TransactionServiceConcurrencyTest {

    private static final String SYMBOL = "AAPL";
    private static final BigDecimal PRICE = new BigDecimal("100.00");
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("10000.00");
    private static final int THREAD_COUNT = 20;

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private HoldingRepository holdingRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private StockPriceClient stockPriceClient;

    @Test
    void concurrentBuys_neverCorruptWalletOrHolding() throws InterruptedException {
        cacheManager.getCache(RedisConfig.STOCK_PRICES_CACHE).evict(SYMBOL);
        when(stockPriceClient.getPrice(SYMBOL)).thenReturn(Optional.of(PRICE));

        User user = new User();
        user.setEmail("concurrency-test-" + System.nanoTime() + "@example.com");
        user.setPasswordHash("irrelevant");
        user.setRole(Role.USER);
        user.setCreatedAt(Instant.now());
        user = userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalance(STARTING_BALANCE);
        walletRepository.save(wallet);

        String email = user.getEmail();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    RetryHelper.executeWithRetry(
                            () -> transactionService.buy(email, new BuyRequestDto(SYMBOL, 1L)),
                            () -> new RuntimeException("retry exhausted"),
                            5
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Basarili: " + successCount.get() + " / Basarisiz: " + failureCount.get());

        Stock stock = stockRepository.findBySymbol(SYMBOL).orElseThrow();
        Wallet finalWallet = walletRepository.findByUser(user).orElseThrow();
        Holding finalHolding = holdingRepository.findByUserAndStock(user, stock).orElseThrow();

        BigDecimal expectedBalance = STARTING_BALANCE
                .subtract(PRICE.multiply(BigDecimal.valueOf(successCount.get())));

        assertThat(successCount.get() + failureCount.get()).isEqualTo(THREAD_COUNT);
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(finalWallet.getBalance()).isEqualByComparingTo(expectedBalance);
        assertThat(finalWallet.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(finalHolding.getQuantity()).isEqualTo((long) successCount.get());
    }
}
