package com.serphenix.portfolio.external;

import com.serphenix.portfolio.exception.ExternalApiException;
import com.serphenix.portfolio.exception.ExternalApiTimeoutException;
import com.serphenix.portfolio.exception.RateLimitExceededException;
import com.serphenix.portfolio.external.dto.FinnhubQuoteResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinnhubStockPriceClient implements StockPriceClient {

    private final RestClient finnhubRestClient;

    @Override
    public Optional<BigDecimal> getPrice(String symbol) {
        try {
            FinnhubQuoteResponseDto response = finnhubRestClient
                    .get()
                    .uri("/quote?symbol={symbol}", symbol)
                    .retrieve()
                    .onStatus(status -> status.value() == 429,
                            (req, res) -> {
                                log.warn("Finnhub rate limit exceeded for symbol {}", symbol);
                                throw new RateLimitExceededException("Rate limit exceeded on finnhub");
                            }
                    )
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (req, res) -> {
                                log.warn("Finnhub returned client error {} for symbol {}", res.getStatusCode(), symbol);
                                throw new ExternalApiException("Error calling Finnhub API: " + res.getStatusCode());
                            }
                    )
                    .onStatus(HttpStatusCode::is5xxServerError,
                            (req, res) -> {
                                log.warn("Finnhub returned server error {} for symbol {}", res.getStatusCode(), symbol);
                                throw new ExternalApiException("Error calling Finnhub API: " + res.getStatusCode());
                            }
                    )
                    .body(FinnhubQuoteResponseDto.class);

            if (response.c().compareTo(BigDecimal.ZERO) == 0) {
                log.debug("Finnhub returned no price for unknown symbol {}", symbol);
                return Optional.empty();
            }

            return Optional.of(response.c());
        } catch (ResourceAccessException e) {
            log.warn("Finnhub request timed out for symbol {}", symbol);
            throw new ExternalApiTimeoutException("Finnhub request timed out");
        }
    }
}
