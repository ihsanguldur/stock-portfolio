package com.serphenix.portfolio.consumer;

import com.serphenix.portfolio.event.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TransactionNotificationConsumer {

    @KafkaListener(topics = "transaction-events", groupId = "portfolio-notification-group")
    public void sendTransactionNotification(TransactionEvent transactionEvent) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[NOTIFICATION] {} - {} {} {} @ {}",
                transactionEvent.email(),
                transactionEvent.type(),
                transactionEvent.symbol(),
                transactionEvent.quantity(),
                transactionEvent.price()
        );
    }
}
