package com.serphenix.portfolio.external;

import java.math.BigDecimal;
import java.util.Optional;

public interface StockPriceClient {
    Optional<BigDecimal> getPrice(String symbol);
}
