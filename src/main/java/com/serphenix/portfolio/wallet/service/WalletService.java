package com.serphenix.portfolio.wallet.service;

import com.serphenix.portfolio.audit.Audited;
import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.auth.repository.UserRepository;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import com.serphenix.portfolio.wallet.dto.request.WalletRequestDto;
import com.serphenix.portfolio.wallet.dto.response.WalletResponseDto;
import com.serphenix.portfolio.wallet.entity.Wallet;
import com.serphenix.portfolio.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    public WalletResponseDto findByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow(
                () -> new IllegalStateException("Wallet not found for user " + user.getId())
        );

        return new WalletResponseDto(
                wallet.getId(),
                wallet.getBalance()
        );
    }

    @Transactional
    @Audited(action = "DEPOSIT", entityType = "WALLET")
    public WalletResponseDto deposit(String email, WalletRequestDto request) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow(
                () -> new IllegalStateException("Wallet not found for user " + user.getId())
        );

        wallet.deposit(request.amount());
        walletRepository.save(wallet);

        return new WalletResponseDto(
                wallet.getId(),
                wallet.getBalance()
        );
    }
}
