package com.serphenix.portfolio.service;

import com.serphenix.portfolio.dto.response.StockResponseDto;
import com.serphenix.portfolio.entity.Stock;
import com.serphenix.portfolio.exception.StockNotFoundException;
import com.serphenix.portfolio.external.StockPriceClient;
import com.serphenix.portfolio.mapper.StockMapper;
import com.serphenix.portfolio.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;

import java.time.Instant;

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

    public StockResponseDto getPrice(String symbol) {

        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new StockNotFoundException("Stock not found by " + symbol));

        stockPriceClient.getPrice(symbol).ifPresentOrElse(
                price -> {
                    stock.setLastPrice(price);
                    stock.setLastUpdate(Instant.now());

                    stockRepository.save(stock);
                },
                () -> {
                    throw new StockNotFoundException("Could not find price for " + symbol);
                }
        );

        return StockMapper.toDto(stock);
    }
}
