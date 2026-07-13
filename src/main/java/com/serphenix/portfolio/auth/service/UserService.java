package com.serphenix.portfolio.auth.service;

import com.serphenix.portfolio.auth.dto.response.UserResponseDto;
import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.auth.mapper.UserMapper;
import com.serphenix.portfolio.auth.repository.UserRepository;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public UserResponseDto findUserByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        return UserMapper.toDto(user);
    }
}
