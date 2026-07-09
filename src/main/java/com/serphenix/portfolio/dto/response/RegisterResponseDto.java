package com.serphenix.portfolio.dto.response;

import com.serphenix.portfolio.entity.enums.Role;

import java.math.BigDecimal;
import java.time.Instant;

public record RegisterResponseDto(
        Long id,
        String email,
        Role role,
        BigDecimal walletBalance,
        Instant createdAt,
        String accessToken,
        String refreshToken
) {}
