package com.serphenix.portfolio.client;

import com.serphenix.portfolio.security.TokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class ServiceClientConfig {

    private final TokenInterceptor tokenInterceptor;

    @Value("${wallet-service.url}")
    private String walletServiceUrl;

    @Bean
    @Qualifier("walletRestClient")
    public RestClient walletRestClient() {
        return RestClient.builder()
                .baseUrl(walletServiceUrl)
                .requestInterceptor(tokenInterceptor)
                .build();
    }
}