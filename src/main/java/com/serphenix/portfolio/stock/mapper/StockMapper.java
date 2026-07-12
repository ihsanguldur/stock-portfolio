package com.serphenix.portfolio.stock.mapper;

import com.serphenix.portfolio.stock.dto.response.StockResponseDto;
import com.serphenix.portfolio.stock.entity.Stock;

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
