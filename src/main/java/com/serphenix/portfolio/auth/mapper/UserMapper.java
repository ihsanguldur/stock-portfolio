package com.serphenix.portfolio.auth.mapper;

import com.serphenix.portfolio.auth.dto.response.UserResponseDto;
import com.serphenix.portfolio.auth.entity.User;

public class UserMapper {
    public static UserResponseDto toDto(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
