package com.serphenix.portfolio.transaction.controller;

import com.serphenix.portfolio.transaction.dto.request.BuyRequestDto;
import com.serphenix.portfolio.transaction.dto.request.SellRequestDto;
import com.serphenix.portfolio.transaction.dto.response.TransactionResponseDto;
import com.serphenix.portfolio.transaction.exception.TransactionConflictException;
import com.serphenix.portfolio.helper.RetryHelper;
import com.serphenix.portfolio.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    public PagedModel<TransactionResponseDto> findAllTransactions(Authentication authentication, Pageable pageable) {
        return transactionService.findAllTransactions(authentication.getName(), pageable);
    }

    @GetMapping("/{id}")
    public TransactionResponseDto findTransaction(Authentication authentication, @PathVariable Long id) {
        return transactionService.findTransaction(authentication.getName(), id);
    }
}
