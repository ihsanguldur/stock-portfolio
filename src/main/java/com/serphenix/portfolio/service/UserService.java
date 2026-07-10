package com.serphenix.portfolio.service;

import com.serphenix.portfolio.dto.response.UserResponseDto;
import com.serphenix.portfolio.entity.User;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import com.serphenix.portfolio.mapper.UserMapper;
import com.serphenix.portfolio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
