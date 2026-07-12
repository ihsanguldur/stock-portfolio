package com.serphenix.portfolio.portfolio.service;

import com.serphenix.portfolio.portfolio.dto.response.HoldingResponseDto;
import com.serphenix.portfolio.portfolio.dto.response.PortfolioResponseDto;
import com.serphenix.portfolio.portfolio.entity.Holding;
import com.serphenix.portfolio.stock.entity.Stock;
import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.portfolio.exception.HoldingNotFoundException;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import com.serphenix.portfolio.stock.exception.StockNotFoundException;
import com.serphenix.portfolio.portfolio.repository.HoldingRepository;
import com.serphenix.portfolio.stock.repository.StockRepository;
import com.serphenix.portfolio.auth.repository.UserRepository;
import com.serphenix.portfolio.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;

    public HoldingResponseDto findHolding(String email, String symbol) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        Stock stock = stockRepository.findBySymbol(symbol).orElseThrow(
                () -> new StockNotFoundException("Stock not found")
        );

        Holding holding = holdingRepository.findByUserAndStock(user, stock).orElseThrow(
                () -> new HoldingNotFoundException("Holding not found")
        );

        return toHoldingResponseDto(holding);
    }

    public PortfolioResponseDto findPortfolio(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        List<Holding> holdings = holdingRepository.findByUser(user);

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;

        List<HoldingResponseDto> holdingResponse = new ArrayList<>();
        for (Holding holding : holdings) {
            HoldingResponseDto holdingResponseDto = toHoldingResponseDto(holding);

            totalValue = totalValue.add(holdingResponseDto.currentValue());
            totalUnrealizedPnl = totalUnrealizedPnl.add(holdingResponseDto.unrealizedPnl());

            holdingResponse.add(holdingResponseDto);
        }

        return new PortfolioResponseDto(
                holdingResponse,
                totalValue,
                totalUnrealizedPnl
        );
    }

    private HoldingResponseDto toHoldingResponseDto(Holding holding) {
        BigDecimal currentPrice = stockService.getPrice(holding.getStock().getSymbol()).lastPrice();
        BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(holding.getQuantity()));
        BigDecimal unrealizedPnl = currentPrice
                .subtract(holding.getAvgCost())
                .multiply(BigDecimal.valueOf(holding.getQuantity()));

        return new HoldingResponseDto(
                holding.getStock().getSymbol(),
                holding.getQuantity(),
                holding.getAvgCost(),
                currentPrice,
                currentValue,
                unrealizedPnl
        );
    }
}
