package com.serphenix.portfolio.stock.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    public static Stock create(String symbol) {
        Stock stock = new Stock();
        stock.symbol = symbol;
        return stock;
    }

    public void updatePrice(BigDecimal price) {
        this.lastPrice = price;
        this.lastUpdate = Instant.now();
    }
}