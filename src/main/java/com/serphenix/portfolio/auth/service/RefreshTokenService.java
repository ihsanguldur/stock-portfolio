package com.serphenix.portfolio.auth.service;

import com.serphenix.portfolio.auth.entity.RefreshToken;
import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpirationMs;

    public RefreshToken create(User user, String tokenValue) {
        RefreshToken refreshToken = RefreshToken.create(
                user,
                tokenValue,
                Instant.now().plusMillis(refreshTokenExpirationMs)
        );

        return refreshTokenRepository.save(refreshToken);
    }
}
