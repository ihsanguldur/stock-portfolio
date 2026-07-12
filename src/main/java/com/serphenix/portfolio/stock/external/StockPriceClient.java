package com.serphenix.portfolio.stock.external;

import java.math.BigDecimal;
import java.util.Optional;

public interface StockPriceClient {
    Optional<BigDecimal> getPrice(String symbol);
}
