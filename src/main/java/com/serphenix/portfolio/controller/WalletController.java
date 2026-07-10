package com.serphenix.portfolio.controller;

import com.serphenix.portfolio.dto.request.WalletRequestDto;
import com.serphenix.portfolio.dto.response.WalletResponseDto;
import com.serphenix.portfolio.exception.ConcurrentUpdateException;
import com.serphenix.portfolio.helper.RetryHelper;
import com.serphenix.portfolio.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public WalletResponseDto getWallet(Authentication authentication) {
        return walletService.findByEmail(authentication.getName());
    }

    @PostMapping("/deposit")
    public WalletResponseDto deposit(Authentication authentication, @Valid @RequestBody WalletRequestDto request) {
        return RetryHelper.executeWithRetry(
                () -> walletService.deposit(authentication.getName(), request),
                () -> new ConcurrentUpdateException("Could not complete deposit due to concurrent updates"),
                5
        );
    }
}
