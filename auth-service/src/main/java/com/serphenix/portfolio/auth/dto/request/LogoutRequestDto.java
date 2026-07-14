package com.serphenix.portfolio.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequestDto(
        @NotBlank(message = "Refresh Token is required")
        String refreshToken
) {
}
