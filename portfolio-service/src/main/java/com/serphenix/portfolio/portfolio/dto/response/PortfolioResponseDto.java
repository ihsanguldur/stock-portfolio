package com.serphenix.portfolio.portfolio.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponseDto(
        List<HoldingResponseDto> holdings,
        BigDecimal totalValue,
        BigDecimal totalUnrealizedPnl
) {
}
