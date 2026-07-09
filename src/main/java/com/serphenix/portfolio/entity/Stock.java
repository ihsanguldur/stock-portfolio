package com.serphenix.portfolio.entity;

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
@Table(name = "stocks")
public class Stock {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "symbol")
    @ToString.Include
    private String symbol;

    @Column(name = "name")
    @ToString.Include
    private String name;

    @Column(name = "last_price")
    @ToString.Include
    private BigDecimal lastPrice;

    @Column(name = "last_update")
    @ToString.Include
    private Instant lastUpdate;
}