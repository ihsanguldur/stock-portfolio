package com.serphenix.portfolio.wallet.entity;

import com.serphenix.portfolio.wallet.exception.InsufficientBalanceException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "wallets")
public class Wallet {

    @Id
    @Column(name = "id")
    @EqualsAndHashCode.Include
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "balance")
    @ToString.Include
    private BigDecimal balance;

    @Version
    @Column(name = "version")
    private Long version;

    public static Wallet create(Long userId, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.userId = userId;
        wallet.balance = balance;
        return wallet;
    }

    public void withdraw(BigDecimal amount) {
        if (this.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        this.balance = this.getBalance().subtract(amount);
    }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
