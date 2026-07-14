package com.serphenix.portfolio.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SellRequestDto(
        @NotBlank(message = "Symbol is required")
        String symbol,
        @Positive(message = "Quantity must be greater than 0")
        Long quantity
) {
}
