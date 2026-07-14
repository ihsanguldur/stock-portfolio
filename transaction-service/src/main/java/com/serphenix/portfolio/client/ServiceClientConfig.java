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

    @Value("${portfolio-service.url}")
    private String portfolioServiceUrl;

    @Value("${stock-service.url}")
    private String stockServiceUrl;

    @Bean
    @Qualifier("walletRestClient")
    public RestClient walletRestClient() {
        return RestClient.builder()
                .baseUrl(walletServiceUrl)
                .requestInterceptor(tokenInterceptor)
                .build();
    }

    @Bean
    @Qualifier("portfolioRestClient")
    public RestClient portfolioRestClient() {
        return RestClient.builder()
                .baseUrl(portfolioServiceUrl)
                .requestInterceptor(tokenInterceptor)
                .build();
    }

    @Bean
    @Qualifier("stockRestClient")
    public RestClient stockRestClient() {
        return RestClient.builder()
                .baseUrl(stockServiceUrl)
                .requestInterceptor(tokenInterceptor)
                .build();
    }
}
