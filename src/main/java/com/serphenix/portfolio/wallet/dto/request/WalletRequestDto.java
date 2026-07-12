package com.serphenix.portfolio.wallet.dto.request;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WalletRequestDto(
        @Positive(message = "Balance must be greater than 0")
        BigDecimal amount
) {
}
