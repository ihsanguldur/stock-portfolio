package com.serphenix.portfolio.portfolio.entity;

import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.stock.entity.Stock;
import com.serphenix.portfolio.portfolio.exception.InsufficientHoldingException;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
@Slf4j
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "holdings")
public class Holding {

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

    @Column(name = "quantity")
    @ToString.Include
    private Long quantity;

    @Column(name = "avg_cost")
    @ToString.Include
    private BigDecimal avgCost;

    @Version
    @Column(name = "version")
    private Long version;

    public static Holding create(User user, Stock stock) {
        Holding holding = new Holding();
        holding.user = user;
        holding.stock = stock;
        holding.avgCost = BigDecimal.ZERO;
        holding.quantity = 0L;
        return holding;
    }

    public void applyBuy(long quantity, BigDecimal price, BigDecimal cost) {
        long newQuantity = this.getQuantity() + quantity;

        BigDecimal newAvgCost = this.getQuantity() == 0 ?
                price :
                this.getAvgCost()
                        .multiply(BigDecimal.valueOf(this.getQuantity()))
                        .add(cost)
                        .divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP);

        this.quantity = newQuantity;
        this.avgCost = newAvgCost;
    }

    public void applySell(long quantity) {
        if (this.getQuantity() < quantity) {
            throw new InsufficientHoldingException("Insufficient holding " + this.quantity + " " + this.getStock().getSymbol());
        }

        this.quantity = this.getQuantity() - quantity;
    }

    public boolean isEmpty() {
        return this.quantity == 0L;
    }
}
