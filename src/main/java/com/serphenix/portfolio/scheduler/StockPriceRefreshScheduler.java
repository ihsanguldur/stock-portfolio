package com.serphenix.portfolio.scheduler;

import com.serphenix.portfolio.entity.Stock;
import com.serphenix.portfolio.event.PriceRefreshRequestEvent;
import com.serphenix.portfolio.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockPriceRefreshScheduler {

    private final HoldingRepository holdingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedRate = 60000)
    public void refreshStockPrices() {
        List<Stock> stocks = holdingRepository.findDistinctStocks();

        for (Stock stock : stocks) {
            kafkaTemplate.send("price-refresh-requests", new PriceRefreshRequestEvent(stock.getSymbol()));
            log.info("Refreshed price for {}", stock.getSymbol());
        }
    }
}
