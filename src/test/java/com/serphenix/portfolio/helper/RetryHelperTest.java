package com.serphenix.portfolio.helper;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class RetryHelperTest {

    @Test
    void executeWithRetrySucceedsOnFirstTryCallsSupplierOnce() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            counter.incrementAndGet();
            return "success";
        };

        String result = RetryHelper.executeWithRetry(supplier, () -> new RuntimeException("test"), 5);

        assertThat(result).isEqualTo("success");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void executeWithRetrySucceedsOnThirdTryCallsSupplierOnce() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            counter.incrementAndGet();
            if (counter.get() < 3) {
                throw new OptimisticLockingFailureException("test");
            }

            return "success";
        };

        String result = RetryHelper.executeWithRetry(supplier, () -> new RuntimeException("test"), 5);

        assertThat(result).isEqualTo("success");
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    void executeWithRetryFailsOnMaxAttemptThrowGivenException() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            counter.incrementAndGet();
            if (counter.get() < 6) {
                throw new OptimisticLockingFailureException("test");
            }

            return "success";
        };

        assertThatThrownBy(() -> {
            RetryHelper.executeWithRetry(supplier, () -> new RuntimeException("test"), 5);
        })
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test");

        assertThat(counter.get()).isEqualTo(5);
    }

    @Test
    void executeWithRetryNonRetryableExceptionThrowImmediatelyWithoutRetry() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            counter.incrementAndGet();
            throw new IllegalArgumentException("not retryable");
        };

        assertThatThrownBy(() ->
                RetryHelper.executeWithRetry(supplier, () -> new RuntimeException("test"), 5)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not retryable");

        assertThat(counter.get()).isEqualTo(1);
    }
}
