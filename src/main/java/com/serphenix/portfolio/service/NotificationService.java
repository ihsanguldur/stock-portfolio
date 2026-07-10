package com.serphenix.portfolio.service;

import com.serphenix.portfolio.entity.enums.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class NotificationService {

    @Async
    public void sendTransactionNotification(String email, TransactionType type, String symbol, Long quantity, BigDecimal price) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[NOTIFICATION] {} - {} {} {} @ {}", email , type, symbol, quantity, price);
    }
}
