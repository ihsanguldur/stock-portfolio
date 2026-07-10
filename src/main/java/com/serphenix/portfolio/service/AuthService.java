package com.serphenix.portfolio.service;

import com.serphenix.portfolio.dto.request.LoginRequestDto;
import com.serphenix.portfolio.dto.request.LogoutRequestDto;
import com.serphenix.portfolio.dto.request.RefreshRequestDto;
import com.serphenix.portfolio.dto.request.RegisterRequestDto;
import com.serphenix.portfolio.dto.response.LoginResponseDto;
import com.serphenix.portfolio.dto.response.RefreshResponseDto;
import com.serphenix.portfolio.dto.response.RegisterResponseDto;
import com.serphenix.portfolio.entity.RefreshToken;
import com.serphenix.portfolio.entity.User;
import com.serphenix.portfolio.entity.Wallet;
import com.serphenix.portfolio.entity.enums.Role;
import com.serphenix.portfolio.exception.EmailAlreadyExistsException;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import com.serphenix.portfolio.repository.RefreshTokenRepository;
import com.serphenix.portfolio.repository.UserRepository;
import com.serphenix.portfolio.repository.WalletRepository;
import com.serphenix.portfolio.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public RegisterResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setCreatedAt(Instant.now());

        User savedUser;
        try {
            savedUser = userRepository.save(user);

            Wallet wallet = new Wallet();
            wallet.setUser(savedUser);
            wallet.setBalance(new BigDecimal("100000"));
            walletRepository.save(wallet);

        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.email());
        }

        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = jwtService.generateRefreshToken();
        refreshTokenService.create(savedUser, refreshToken);

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

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();
        refreshTokenService.create(user, refreshToken);

        log.info("User logged in: {}", user.getEmail());

        return new LoginResponseDto(accessToken, refreshToken);
    }

    public RefreshResponseDto refresh(RefreshRequestDto request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new InvalidCredentialsException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new InvalidCredentialsException("Refresh token has expired");
        }

        User user = refreshToken.getUser();

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken();
        refreshTokenService.create(user, newRefreshToken);

        return new RefreshResponseDto(accessToken, newRefreshToken);
    }

    public void logout(LogoutRequestDto request) {
        refreshTokenRepository.findByToken(request.refreshToken())
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("User logged out: {}", token.getUser().getEmail());
                });
    }
}
