package com.serphenix.portfolio.auth.dto.response;

public record RefreshResponseDto(
        String accessToken,
        String refreshToken
) {
}
