package com.serphenix.portfolio.transaction.event;

import com.serphenix.portfolio.transaction.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionEvent(
        String email,
        TransactionType type,
        String symbol,
        Long quantity,
        BigDecimal price,
        Instant timestamp
) {
}
