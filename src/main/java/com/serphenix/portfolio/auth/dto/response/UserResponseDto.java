package com.serphenix.portfolio.auth.dto.response;

import com.serphenix.portfolio.auth.entity.enums.Role;

import java.time.Instant;

public record UserResponseDto(
        Long id,
        String email,
        Role role,
        Instant createdAt
) {
}
