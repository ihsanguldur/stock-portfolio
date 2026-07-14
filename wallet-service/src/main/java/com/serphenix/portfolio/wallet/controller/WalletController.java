package com.serphenix.portfolio.wallet.controller;

import com.serphenix.portfolio.helper.RetryHelper;
import com.serphenix.portfolio.security.JwtPrincipal;
import com.serphenix.portfolio.wallet.dto.request.WalletRequestDto;
import com.serphenix.portfolio.wallet.dto.response.WalletResponseDto;
import com.serphenix.portfolio.wallet.exception.ConcurrentUpdateException;
import com.serphenix.portfolio.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public WalletResponseDto getWallet(@AuthenticationPrincipal JwtPrincipal principal) {
        return walletService.find(principal.userId());
    }

    @PostMapping("/deposit")
    public WalletResponseDto deposit(@AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody WalletRequestDto request) {
        return RetryHelper.executeWithRetry(
                () -> walletService.deposit(principal.userId(), request),
                () -> new ConcurrentUpdateException("Could not complete deposit due to concurrent updates"),
                5
        );
    }

    @PostMapping
    public WalletResponseDto open(@AuthenticationPrincipal JwtPrincipal principal) {
        return walletService.open(principal.userId());
    }

    @PostMapping("/withdraw")
    public WalletResponseDto withdraw(@AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody WalletRequestDto request) {
        return RetryHelper.executeWithRetry(
                () -> walletService.withdraw(principal.userId(), request),
                () -> new ConcurrentUpdateException("Could not complete withdraw due to concurrent updates"),
                5
        );
    }

    @DeleteMapping
    public void close(@AuthenticationPrincipal JwtPrincipal principal) {
        walletService.close(principal.userId());
    }
}
