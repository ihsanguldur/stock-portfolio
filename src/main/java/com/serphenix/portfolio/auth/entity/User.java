package com.serphenix.portfolio.auth.entity;

import com.serphenix.portfolio.auth.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "email")
    @ToString.Include
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "role")
    @ToString.Include
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "created_at")
    @ToString.Include
    private Instant createdAt;

    public static User create(String email, String passwordHash) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.role = Role.USER;
        user.createdAt = Instant.now();
        return user;
    }
}
