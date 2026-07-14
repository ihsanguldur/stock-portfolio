package com.serphenix.portfolio.client.dto;

import java.math.BigDecimal;

public record WalletRequestDto(
        BigDecimal amount
) {
}