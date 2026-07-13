package com.serphenix.portfolio.portfolio.service;

import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.auth.repository.UserRepository;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import com.serphenix.portfolio.portfolio.dto.response.HoldingResponseDto;
import com.serphenix.portfolio.portfolio.dto.response.PortfolioResponseDto;
import com.serphenix.portfolio.portfolio.entity.Holding;
import com.serphenix.portfolio.portfolio.exception.HoldingNotFoundException;
import com.serphenix.portfolio.portfolio.repository.HoldingRepository;
import com.serphenix.portfolio.stock.entity.Stock;
import com.serphenix.portfolio.stock.exception.StockNotFoundException;
import com.serphenix.portfolio.stock.repository.StockRepository;
import com.serphenix.portfolio.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        Holding holding = holdingRepository.findByUserIdAndStockId(user.getId(), stock.getId()).orElseThrow(
                () -> new HoldingNotFoundException("Holding not found")
        );

        return toHoldingResponseDto(holding, stock.getSymbol());
    }

    public PortfolioResponseDto findPortfolio(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        List<Holding> holdings = holdingRepository.findByUserId(user.getId());

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;

        Map<Long, String> symbolsByStockId = stockRepository.findAllById(
                holdings.stream().map(Holding::getStockId).distinct().toList()
        ).stream().collect(Collectors.toMap(Stock::getId, Stock::getSymbol));

        List<HoldingResponseDto> holdingResponse = new ArrayList<>();
        for (Holding holding : holdings) {
            HoldingResponseDto holdingResponseDto = toHoldingResponseDto(holding, symbolsByStockId.get(holding.getStockId()));

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

    private HoldingResponseDto toHoldingResponseDto(Holding holding, String symbol) {
        BigDecimal currentPrice = stockService.getPrice(symbol).lastPrice();
        BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(holding.getQuantity()));
        BigDecimal unrealizedPnl = currentPrice
                .subtract(holding.getAvgCost())
                .multiply(BigDecimal.valueOf(holding.getQuantity()));

        return new HoldingResponseDto(
                symbol,
                holding.getQuantity(),
                holding.getAvgCost(),
                currentPrice,
                currentValue,
                unrealizedPnl
        );
    }
}
