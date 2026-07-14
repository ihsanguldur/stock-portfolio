package com.serphenix.portfolio.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockResponseDto(
        Long id,
        String symbol,
        String name,
        BigDecimal lastPrice,
        Instant lastUpdate
) {
}