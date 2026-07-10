package com.serphenix.portfolio.controller;

import com.serphenix.portfolio.dto.request.BuyRequestDto;
import com.serphenix.portfolio.dto.request.SellRequestDto;
import com.serphenix.portfolio.dto.response.TransactionResponseDto;
import com.serphenix.portfolio.exception.TransactionConflictException;
import com.serphenix.portfolio.helper.RetryHelper;
import com.serphenix.portfolio.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/buy")
    public TransactionResponseDto buy(Authentication authentication, @Valid @RequestBody BuyRequestDto request) {
        return RetryHelper.executeWithRetry(
                () -> transactionService.buy(authentication.getName(), request),
                () -> new TransactionConflictException("Could not complete transaction due to concurrent updates"),
                5
        );
    }

    @PostMapping("/sell")
    public TransactionResponseDto sell(Authentication authentication, @Valid @RequestBody SellRequestDto request) {
        return RetryHelper.executeWithRetry(
                () -> transactionService.sell(authentication.getName(), request),
                () -> new TransactionConflictException("Could not complete transaction due to concurrent updates"),
                5
        );
    }
}
