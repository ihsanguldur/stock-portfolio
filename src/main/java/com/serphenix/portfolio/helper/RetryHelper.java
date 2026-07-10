package com.serphenix.portfolio.helper;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class RetryHelper {

    public static <T> T executeWithRetry(Supplier<T> supplier, Supplier<RuntimeException> onExhausted, int maxAttempts) {
        int attempts = 0;
        long delay = 10;

        while (true) {
            try {
                return supplier.get();
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                attempts++;
                if (attempts >= maxAttempts) {
                    throw onExhausted.get();
                }

                try {
                    Thread.sleep(delay + ThreadLocalRandom.current().nextLong(delay));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw onExhausted.get();
                }
                delay *= 2;
            }
        }
    }
}
