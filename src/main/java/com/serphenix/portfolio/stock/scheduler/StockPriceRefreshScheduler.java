package com.serphenix.portfolio.stock.scheduler;

import com.serphenix.portfolio.portfolio.repository.HoldingRepository;
import com.serphenix.portfolio.stock.entity.Stock;
import com.serphenix.portfolio.stock.event.PriceRefreshRequestEvent;
import com.serphenix.portfolio.stock.repository.StockRepository;
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
    private final StockRepository stockRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedRate = 60000)
    public void refreshStockPrices() {
        List<Long> stockIds = holdingRepository.findDistinctStockIds();
        List<Stock> stocks = stockRepository.findAllById(stockIds);

        for (Stock stock : stocks) {
            kafkaTemplate.send("price-refresh-requests", new PriceRefreshRequestEvent(stock.getSymbol()));
            log.info("Refreshed price for {}", stock.getSymbol());
        }
    }
}
