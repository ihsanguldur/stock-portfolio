package com.serphenix.portfolio.dto.response;

import com.serphenix.portfolio.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponseDto(
        Long id,
        String symbol,
        TransactionType type,
        Long quantity,
        BigDecimal price,
        Instant timestamp
) implements Identifiable {
}
