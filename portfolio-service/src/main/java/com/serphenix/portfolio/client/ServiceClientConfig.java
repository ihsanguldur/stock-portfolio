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

    @Value("${stock-service.url}")
    private String stockServiceUrl;

    @Bean
    @Qualifier("stockRestClient")
    public RestClient stockRestClient() {
        return RestClient.builder()
                .baseUrl(stockServiceUrl)
                .requestInterceptor(tokenInterceptor)
                .build();
    }
}