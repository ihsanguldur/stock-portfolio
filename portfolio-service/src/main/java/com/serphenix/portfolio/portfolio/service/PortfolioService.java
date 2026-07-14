package com.serphenix.portfolio.portfolio.service;

import com.serphenix.portfolio.client.StockClient;
import com.serphenix.portfolio.client.dto.StockResponseDto;
import com.serphenix.portfolio.portfolio.dto.request.HoldingBuyRequestDto;
import com.serphenix.portfolio.portfolio.dto.request.HoldingSellRequestDto;
import com.serphenix.portfolio.portfolio.dto.response.HoldingResponseDto;
import com.serphenix.portfolio.portfolio.dto.response.PortfolioResponseDto;
import com.serphenix.portfolio.portfolio.entity.Holding;
import com.serphenix.portfolio.portfolio.exception.HoldingNotFoundException;
import com.serphenix.portfolio.portfolio.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final HoldingRepository holdingRepository;
    private final StockClient stockClient;

    public HoldingResponseDto findHolding(Long userId, String symbol) {
        StockResponseDto stock = stockClient.getBySymbol(symbol);

        Holding holding = holdingRepository.findByUserIdAndStockId(userId, stock.id()).orElseThrow(
                () -> new HoldingNotFoundException("Holding not found")
        );

        return toHoldingResponseDto(holding, stock);
    }

    public PortfolioResponseDto findPortfolio(Long userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;

        Map<Long, StockResponseDto> symbolsByStockId = stockClient.getByIds(
                holdings.stream().map(Holding::getStockId).distinct().toList()
        ).stream().collect(Collectors.toMap(StockResponseDto::id, (stock) -> stock));

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

    @Transactional
    public void applyBuy(Long userId, HoldingBuyRequestDto request) {
        StockResponseDto stock = stockClient.getBySymbol(request.symbol());

        Holding holding = holdingRepository.findByUserIdAndStockId(userId, stock.id()).orElseGet(
                () -> Holding.create(userId, stock.id())
        );

        BigDecimal cost = request.price().multiply(BigDecimal.valueOf(request.quantity()));
        holding.applyBuy(request.quantity(), request.price(), cost);

        holdingRepository.save(holding);
    }

    @Transactional
    public void applySell(Long userId, HoldingSellRequestDto request) {
        StockResponseDto stock = stockClient.getBySymbol(request.symbol());

        Holding holding = holdingRepository.findByUserIdAndStockId(userId, stock.id()).orElseThrow(
                () -> new HoldingNotFoundException("Holding not found")
        );

        holding.applySell(request.quantity(), request.symbol());

        if (holding.isEmpty()) {
            holdingRepository.delete(holding);
        } else {
            holdingRepository.save(holding);
        }
    }

    private HoldingResponseDto toHoldingResponseDto(Holding holding, StockResponseDto stock) {
        BigDecimal currentPrice = stock.lastPrice();
        BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(holding.getQuantity()));
        BigDecimal unrealizedPnl = currentPrice
                .subtract(holding.getAvgCost())
                .multiply(BigDecimal.valueOf(holding.getQuantity()));

        return new HoldingResponseDto(
                stock.symbol(),
                holding.getQuantity(),
                holding.getAvgCost(),
                currentPrice,
                currentValue,
                unrealizedPnl
        );
    }
}
