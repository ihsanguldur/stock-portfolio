package com.serphenix.portfolio.transaction.entity;

import com.serphenix.portfolio.transaction.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "id")
    @EqualsAndHashCode.Include
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    @ToString.Include
    private TransactionType type;

    @Column(name = "quantity")
    @ToString.Include
    private Long quantity;

    @Column(name = "price")
    @ToString.Include
    private BigDecimal price;

    @Column(name = "timestamp")
    @ToString.Include
    private Instant timestamp;

    public static Transaction create(Long userId, Long stockId, TransactionType type, Long quantity, BigDecimal price) {
        Transaction transaction = new Transaction();
        transaction.userId = userId;
        transaction.stockId = stockId;
        transaction.type = type;
        transaction.quantity = quantity;
        transaction.price = price;
        transaction.timestamp = Instant.now();
        return transaction;
    }
}
