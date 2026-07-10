package com.serphenix.portfolio.scheduler;

import com.serphenix.portfolio.entity.Stock;
import com.serphenix.portfolio.repository.HoldingRepository;
import com.serphenix.portfolio.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockPriceRefreshScheduler {

    private final HoldingRepository holdingRepository;
    private final StockService stockService;

    @Scheduled(fixedRate = 60000)
    public void refreshStockPrices() {
        List<Stock> stocks = holdingRepository.findDistinctStocks();

        for (Stock stock : stocks) {
            try {
                stockService.getPrice(stock.getSymbol());
                log.info("Refreshed price for {}", stock.getSymbol());
            } catch (Exception e) {
                log.warn("Failed to refresh price for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }
    }
}
