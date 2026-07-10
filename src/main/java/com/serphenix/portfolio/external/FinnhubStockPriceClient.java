package com.serphenix.portfolio.external;

import com.serphenix.portfolio.exception.ExternalApiException;
import com.serphenix.portfolio.exception.ExternalApiTimeoutException;
import com.serphenix.portfolio.exception.RateLimitExceededException;
import com.serphenix.portfolio.external.dto.FinnhubQuoteResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
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
                                throw new RateLimitExceededException("Rate limit exceeded on finnhub");
                            }
                    )
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (req, res) -> {
                                throw new ExternalApiException("Error calling Finnhub API: " + res.getStatusCode());
                            }
                    )
                    .onStatus(HttpStatusCode::is5xxServerError,
                            (req, res) -> {
                                throw new ExternalApiException("Error calling Finnhub API: " + res.getStatusCode());
                            }
                    )
                    .body(FinnhubQuoteResponseDto.class);

            if (response.c().compareTo(BigDecimal.ZERO) == 0) {
                return Optional.empty();
            }

            return Optional.of(response.c());
        } catch (ResourceAccessException e) {
            throw new ExternalApiTimeoutException("Finnhub request timed out");
        }
    }
}
