package com.serphenix.portfolio.dto.response;

public record RefreshResponseDto(
        String accessToken,
        String refreshToken
) {
}
