package com.serphenix.portfolio.stock.event;

public record PriceRefreshRequestEvent(
        String symbol
) {
}
