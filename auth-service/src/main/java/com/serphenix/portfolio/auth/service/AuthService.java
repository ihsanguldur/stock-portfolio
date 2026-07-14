package com.serphenix.portfolio.auth.service;

import com.serphenix.portfolio.auth.dto.request.LoginRequestDto;
import com.serphenix.portfolio.auth.dto.request.LogoutRequestDto;
import com.serphenix.portfolio.auth.dto.request.RefreshRequestDto;
import com.serphenix.portfolio.auth.dto.request.RegisterRequestDto;
import com.serphenix.portfolio.auth.dto.response.LoginResponseDto;
import com.serphenix.portfolio.auth.dto.response.RefreshResponseDto;
import com.serphenix.portfolio.auth.dto.response.RegisterResponseDto;
import com.serphenix.portfolio.auth.entity.RefreshToken;
import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.auth.exception.EmailAlreadyExistsException;
import com.serphenix.portfolio.auth.repository.RefreshTokenRepository;
import com.serphenix.portfolio.auth.repository.UserRepository;
import com.serphenix.portfolio.client.WalletClient;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import com.serphenix.portfolio.helper.CompensationHelper;
import com.serphenix.portfolio.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WalletClient walletClient;

    public RegisterResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.email());
        }

        User user = User.create(request.email(), passwordEncoder.encode(request.password()));

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.email());
        }

        String accessToken = jwtService.generateAccessToken(savedUser.getEmail(), savedUser.getId(), savedUser.getRole().name());
        String refreshToken = jwtService.generateRefreshToken();
        refreshTokenService.create(savedUser, refreshToken);

        try {
            walletClient.open(accessToken);
        } catch (RestClientException e) {
            log.error("Failed to open wallet for user {}, rolling back registration", savedUser.getEmail(), e);
            CompensationHelper.compensate(() -> walletClient.close(accessToken),
                    "close orphaned wallet for user " + savedUser.getEmail());
            refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
            userRepository.delete(savedUser);
            throw e;
        }

        log.info("New user registered: {}", savedUser.getEmail());

        return new RegisterResponseDto(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getCreatedAt(),
                accessToken,
                refreshToken
        );
    }

    public LoginResponseDto login(LoginRequestDto request) {

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Failed login attempt for email: {}", request.email());
                    return new InvalidCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email: {}", request.email());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken();
        refreshTokenService.create(user, refreshToken);

        log.info("User logged in: {}", user.getEmail());

        return new LoginResponseDto(accessToken, refreshToken);
    }

    public RefreshResponseDto refresh(RefreshRequestDto request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        refreshToken.validate();

        User user = refreshToken.getUser();

        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getId(), user.getRole().name());
        String newRefreshToken = jwtService.generateRefreshToken();
        refreshTokenService.create(user, newRefreshToken);

        return new RefreshResponseDto(accessToken, newRefreshToken);
    }

    public void logout(LogoutRequestDto request) {
        refreshTokenRepository.findByToken(request.refreshToken())
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                    log.info("User logged out: {}", token.getUser().getEmail());
                });
    }
}
