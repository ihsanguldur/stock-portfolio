package com.serphenix.portfolio.wallet.service;

import com.serphenix.portfolio.audit.Audited;
import com.serphenix.portfolio.wallet.dto.request.WalletRequestDto;
import com.serphenix.portfolio.wallet.dto.response.WalletResponseDto;
import com.serphenix.portfolio.wallet.entity.Wallet;
import com.serphenix.portfolio.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletResponseDto find(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow(
                () -> new IllegalStateException("Wallet not found for user " + userId)
        );

        return new WalletResponseDto(
                wallet.getId(),
                wallet.getBalance()
        );
    }

    @Transactional
    @Audited(action = "DEPOSIT", entityType = "WALLET")
    public WalletResponseDto deposit(Long userId, WalletRequestDto request) {
        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow(
                () -> new IllegalStateException("Wallet not found for user " + userId)
        );

        wallet.deposit(request.amount());
        walletRepository.save(wallet);

        return new WalletResponseDto(
                wallet.getId(),
                wallet.getBalance()
        );
    }

    public WalletResponseDto open(Long userId) {
        Wallet wallet = Wallet.create(userId, new BigDecimal("100000"));
        walletRepository.save(wallet);

        return new WalletResponseDto(
                wallet.getId(),
                wallet.getBalance()
        );
    }

    @Transactional
    @Audited(action = "WITHDRAW", entityType = "WALLET")
    public WalletResponseDto withdraw(Long userId, WalletRequestDto request) {
        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow(
                () -> new IllegalStateException("Wallet not found for user " + userId)
        );

        wallet.withdraw(request.amount());
        walletRepository.save(wallet);

        return new WalletResponseDto(
                wallet.getId(),
                wallet.getBalance()
        );
    }

    @Transactional
    public void close(Long userId) {
        walletRepository.findByUserId(userId).ifPresent(walletRepository::delete);
    }
}
