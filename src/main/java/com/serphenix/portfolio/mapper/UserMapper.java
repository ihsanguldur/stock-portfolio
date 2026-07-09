package com.serphenix.portfolio.mapper;

import com.serphenix.portfolio.dto.UserResponseDto;
import com.serphenix.portfolio.entity.User;

public class UserMapper {
    public static UserResponseDto toDto(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getWalletBalance(),
                user.getCreatedAt()
        );
    }
}
