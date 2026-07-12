package com.serphenix.portfolio.auth.service;

import com.serphenix.portfolio.auth.service.AuthService;
import com.serphenix.portfolio.auth.dto.request.LoginRequestDto;
import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import com.serphenix.portfolio.auth.repository.UserRepository;
import com.serphenix.portfolio.security.JwtService;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    @Test
    void loginWithNonExistsEmailThrowsSameExceptionAsWrongPassword() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("test@example.com", "12345678")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginWithIncorrectPasswordThrowsSameExceptionAsNonExistsEmail() {
        User user = User.create("test@example.com", "hashed-password");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("test@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }
}
