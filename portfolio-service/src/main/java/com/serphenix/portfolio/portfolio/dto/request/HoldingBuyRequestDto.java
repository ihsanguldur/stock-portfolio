package com.serphenix.portfolio.portfolio.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record HoldingBuyRequestDto(
        @NotBlank(message = "Symbol is required")
        String symbol,
        @Positive(message = "Quantity must be greater than 0")
        Long quantity,
        @Positive(message = "Price must be greater than 0")
        BigDecimal price
) {
}
