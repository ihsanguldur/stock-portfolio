package com.serphenix.portfolio.client;

import com.serphenix.portfolio.client.dto.WalletRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class WalletClient {

    private final RestClient walletRestClient;

    public void withdraw(WalletRequestDto request) {
        walletRestClient.post()
                .uri("/wallet/withdraw")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void deposit(WalletRequestDto request) {
        walletRestClient.post()
                .uri("/wallet/deposit")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}