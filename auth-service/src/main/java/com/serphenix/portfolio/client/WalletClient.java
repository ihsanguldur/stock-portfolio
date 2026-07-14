package com.serphenix.portfolio.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class WalletClient {

    private final RestClient walletRestClient;

    public void open(String accessToken) {
        walletRestClient.post()
                .uri("/wallet")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    public void close(String accessToken) {
        walletRestClient.delete()
                .uri("/wallet")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }
}