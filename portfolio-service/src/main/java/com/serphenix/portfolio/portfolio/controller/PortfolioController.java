package com.serphenix.portfolio.portfolio.controller;

import com.serphenix.portfolio.helper.RetryHelper;
import com.serphenix.portfolio.portfolio.dto.request.HoldingBuyRequestDto;
import com.serphenix.portfolio.portfolio.dto.request.HoldingSellRequestDto;
import com.serphenix.portfolio.portfolio.dto.response.HoldingResponseDto;
import com.serphenix.portfolio.portfolio.dto.response.PortfolioResponseDto;
import com.serphenix.portfolio.portfolio.exception.HoldingConflictException;
import com.serphenix.portfolio.portfolio.service.PortfolioService;
import com.serphenix.portfolio.security.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public PortfolioResponseDto findPortfolio(@AuthenticationPrincipal JwtPrincipal principal) {
        return portfolioService.findPortfolio(principal.userId());
    }

    @GetMapping("/holdings/{symbol}")
    public HoldingResponseDto findHolding(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable String symbol) {
        return portfolioService.findHolding(principal.userId(), symbol);
    }

    @PostMapping("/holdings/buy")
    public void applyBuy(@AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody HoldingBuyRequestDto request) {
        RetryHelper.executeWithRetry(
                () -> {
                    portfolioService.applyBuy(principal.userId(), request);
                    return null;
                },
                () -> new HoldingConflictException("Could not complete buy holding due to concurrent updates"),
                5
        );
    }

    @PostMapping("/holdings/sell")
    public void applySell(@AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody HoldingSellRequestDto request) {
        RetryHelper.executeWithRetry(
                () -> {
                    portfolioService.applySell(principal.userId(), request);
                    return null;
                },
                () -> new HoldingConflictException("Could not complete sell holding due to concurrent updates"),
                5
        );
    }
}
