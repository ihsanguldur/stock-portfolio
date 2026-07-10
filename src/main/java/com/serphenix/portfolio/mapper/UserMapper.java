package com.serphenix.portfolio.mapper;

import com.serphenix.portfolio.dto.response.UserResponseDto;
import com.serphenix.portfolio.entity.User;

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
