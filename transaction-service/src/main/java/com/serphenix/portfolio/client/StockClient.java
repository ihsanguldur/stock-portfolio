package com.serphenix.portfolio.client;

import com.serphenix.portfolio.client.dto.StockResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StockClient {

    private final RestClient stockRestClient;

    public StockResponseDto getBySymbol(String symbol) {
        return stockRestClient.get()
                .uri("/stocks/{symbol}", symbol)
                .retrieve()
                .body(StockResponseDto.class);
    }

    public List<StockResponseDto> getByIds(List<Long> ids) {
        return stockRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/stocks/by-ids").queryParam("ids", ids).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
