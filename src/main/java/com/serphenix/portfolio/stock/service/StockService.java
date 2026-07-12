package com.serphenix.portfolio.stock.service;

import com.serphenix.portfolio.config.RedisConfig;
import com.serphenix.portfolio.stock.dto.response.StockResponseDto;
import com.serphenix.portfolio.stock.entity.Stock;
import com.serphenix.portfolio.stock.exception.StockNotFoundException;
import com.serphenix.portfolio.stock.external.StockPriceClient;
import com.serphenix.portfolio.stock.mapper.StockMapper;
import com.serphenix.portfolio.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceClient stockPriceClient;

    public PagedModel<StockResponseDto> findAll(String search, Pageable pageable) {
        Page<Stock> stocks = (search == null || search.isBlank())
                ? stockRepository.findAll(pageable)
                : stockRepository.search(search, pageable);

        return new PagedModel<>(stocks.map(StockMapper::toDto));
    }

    @Cacheable(cacheNames = RedisConfig.STOCK_PRICES_CACHE)
    public StockResponseDto getPrice(String symbol) {

        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new StockNotFoundException("Stock not found by " + symbol));

        stockPriceClient.getPrice(symbol).ifPresentOrElse(
                price -> {
                    stock.updatePrice(price);
                    stockRepository.save(stock);
                },
                () -> {
                    throw new StockNotFoundException("Could not find price for " + symbol);
                }
        );

        return StockMapper.toDto(stock);
    }
}
