package com.serphenix.portfolio.portfolio.dto.response;

import java.math.BigDecimal;

public record HoldingResponseDto(
        String symbol,
        Long quantity,
        BigDecimal avgCost,
        BigDecimal currentPrice,
        BigDecimal currentValue,
        BigDecimal unrealizedPnl
) {
}
