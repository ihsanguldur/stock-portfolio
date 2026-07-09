package com.serphenix.portfolio.dto.response;

public record LoginResponseDto(
        String accessToken,
        String refreshToken
) {
}