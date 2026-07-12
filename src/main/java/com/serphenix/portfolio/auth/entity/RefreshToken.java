package com.serphenix.portfolio.auth.entity;

import com.serphenix.portfolio.exception.InvalidCredentialsException;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @Column(name = "id")
    @ToString.Include
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token")
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Column(name = "revoked")
    private boolean revoked;

    public static RefreshToken create(User user, String token, Instant expiryDate) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.user = user;
        refreshToken.token = token;
        refreshToken.expiryDate = expiryDate;
        refreshToken.revoked = false;
        return refreshToken;
    }

    public void validate() {
        if (this.revoked) {
            throw new InvalidCredentialsException("Refresh token has been revoked");
        }

        if (this.expiryDate.isBefore(Instant.now())) {
            throw new InvalidCredentialsException("Refresh token has expired");
        }
    }

    public void revoke() {
        this.revoked = true;
    }
}
