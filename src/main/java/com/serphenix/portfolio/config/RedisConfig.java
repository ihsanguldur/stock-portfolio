package com.serphenix.portfolio.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import com.serphenix.portfolio.dto.response.StockResponseDto;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    public static final String STOCK_PRICES_CACHE = "stock_prices";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, JsonMapper jsonMapper) {
        RedisCacheConfiguration stockPriceConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.
                        fromSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, StockResponseDto.class))
                );

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(STOCK_PRICES_CACHE, stockPriceConfig)
                .build();
    }
}
