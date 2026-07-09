package com.serphenix.portfolio.dto.response;

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
