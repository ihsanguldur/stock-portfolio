package com.serphenix.portfolio.auth.dto.response;

public record LoginResponseDto(
        String accessToken,
        String refreshToken
) {
}