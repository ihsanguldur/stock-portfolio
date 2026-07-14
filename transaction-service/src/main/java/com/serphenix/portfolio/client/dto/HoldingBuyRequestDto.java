package com.serphenix.portfolio.client.dto;

import java.math.BigDecimal;

public record HoldingBuyRequestDto(
        String symbol,
        Long quantity,
        BigDecimal price
) {
}