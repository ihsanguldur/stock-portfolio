package com.serphenix.portfolio.wallet.dto.response;

import com.serphenix.portfolio.dto.response.Identifiable;

import java.math.BigDecimal;

public record WalletResponseDto(
        Long id,
        BigDecimal balance
) implements Identifiable {
}
