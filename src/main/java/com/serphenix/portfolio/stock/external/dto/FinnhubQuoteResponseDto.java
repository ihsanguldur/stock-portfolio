package com.serphenix.portfolio.stock.external.dto;

import java.math.BigDecimal;

public record FinnhubQuoteResponseDto(
        BigDecimal c
) {
}
