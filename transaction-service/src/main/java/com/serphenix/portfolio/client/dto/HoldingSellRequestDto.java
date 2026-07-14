package com.serphenix.portfolio.client.dto;

public record HoldingSellRequestDto(
        String symbol,
        Long quantity
) {
}