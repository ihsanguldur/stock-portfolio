package com.serphenix.portfolio.dto.response;

import java.math.BigDecimal;

public record WalletResponseDto(
        Long id,
        BigDecimal balance
) implements Identifiable {
}
