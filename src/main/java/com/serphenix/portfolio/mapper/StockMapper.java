package com.serphenix.portfolio.mapper;

import com.serphenix.portfolio.dto.StockResponseDto;
import com.serphenix.portfolio.entity.Stock;

public class StockMapper {
    public static StockResponseDto toDto(Stock stock) {
        return new StockResponseDto(
                stock.getId(),
                stock.getSymbol(),
                stock.getName(),
                stock.getLastPrice(),
                stock.getLastUpdate()
        );
    }
}
