package com.serphenix.portfolio.stock.consumer;

import com.serphenix.portfolio.stock.event.PriceRefreshRequestEvent;
import com.serphenix.portfolio.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockPriceUpdateConsumer {

    private final StockService stockService;

    @KafkaListener(topics = "price-refresh-requests", groupId = "portfolio-price-update-group")
    public void handlePriceRefreshRequest(PriceRefreshRequestEvent event) {
        try {
            stockService.getPrice(event.symbol());
            log.info("Refreshed price for {}", event.symbol());
        } catch (Exception e) {
            log.warn("Failed to refresh price for {}: {}", event.symbol(), e.getMessage());
        }
    }

}
