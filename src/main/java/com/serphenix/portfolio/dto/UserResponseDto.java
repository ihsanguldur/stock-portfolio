package com.serphenix.portfolio.dto;

import com.serphenix.portfolio.entity.enums.Role;

import java.math.BigDecimal;
import java.time.Instant;

public record UserResponseDto(
        Long id,
        String email,
        Role role,
        BigDecimal walletBalance,
        Instant createdAt
) {
}
