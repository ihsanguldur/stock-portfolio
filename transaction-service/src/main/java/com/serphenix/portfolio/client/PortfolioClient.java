package com.serphenix.portfolio.client;

import com.serphenix.portfolio.client.dto.HoldingBuyRequestDto;
import com.serphenix.portfolio.client.dto.HoldingSellRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class PortfolioClient {

    private final RestClient portfolioRestClient;

    public void applyBuy(HoldingBuyRequestDto request) {
        portfolioRestClient.post()
                .uri("/portfolio/holdings/buy")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void applySell(HoldingSellRequestDto request) {
        portfolioRestClient.post()
                .uri("/portfolio/holdings/sell")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}