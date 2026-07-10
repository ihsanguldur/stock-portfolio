package com.serphenix.portfolio.controller;

import com.serphenix.portfolio.dto.response.HoldingResponseDto;
import com.serphenix.portfolio.dto.response.PortfolioResponseDto;
import com.serphenix.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public PortfolioResponseDto findPortfolio(Authentication authentication) {
        return portfolioService.findPortfolio(authentication.getName());
    }

    @GetMapping("/holdings/{symbol}")
    public HoldingResponseDto findHolding(Authentication authentication, @PathVariable String symbol) {
        return portfolioService.findHolding(authentication.getName(), symbol);
    }
}
