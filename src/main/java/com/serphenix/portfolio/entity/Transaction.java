package com.serphenix.portfolio.entity;

import com.serphenix.portfolio.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "id")
    @EqualsAndHashCode.Include
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

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
}
