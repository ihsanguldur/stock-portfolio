package com.serphenix.portfolio.entity;

import com.serphenix.portfolio.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
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
}
