package com.serphenix.portfolio.service;

import com.serphenix.portfolio.audit.Audited;
import com.serphenix.portfolio.dto.request.BuyRequestDto;
import com.serphenix.portfolio.dto.request.SellRequestDto;
import com.serphenix.portfolio.dto.response.TransactionResponseDto;
import com.serphenix.portfolio.entity.*;
import com.serphenix.portfolio.entity.enums.TransactionType;
import com.serphenix.portfolio.exception.*;
import com.serphenix.portfolio.mapper.TransactionMapper;
import com.serphenix.portfolio.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;
    private final WalletRepository walletRepository;
    private final HoldingRepository holdingRepository;

    @Transactional
    @Audited(action = "BUY_STOCK", entityType = "TRANSACTION")
    public TransactionResponseDto buy(String email, BuyRequestDto request) {

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        Stock stock = stockRepository.findBySymbol(request.symbol()).orElseThrow(
                () -> new StockNotFoundException("Stock not found")
        );

        BigDecimal price = stockService.getPrice(request.symbol()).lastPrice();
        BigDecimal cost = price.multiply(BigDecimal.valueOf(request.quantity()));

        Wallet wallet = walletRepository.findByUser(user).orElseThrow(
                () -> new IllegalStateException("Wallet not found for user " + user.getId())
        );

        if (wallet.getBalance().compareTo(cost) < 0) {
            throw new InsufficientBalanceException("Insufficient balance to buy " + request.quantity() + " " + request.symbol());
        }

        wallet.setBalance(wallet.getBalance().subtract(cost));
        walletRepository.save(wallet);

        Holding holding = holdingRepository.findByUserAndStock(user, stock).orElseGet(
                () -> {
                    Holding h = new Holding();
                    h.setUser(user);
                    h.setStock(stock);
                    h.setQuantity(0L);
                    h.setAvgCost(BigDecimal.ZERO);

                    return h;
                }
        );

        long newQuantity = holding.getQuantity() + request.quantity();

        BigDecimal newAvgCost = holding.getQuantity() == 0 ?
                price :
                holding.getAvgCost()
                        .multiply(BigDecimal.valueOf(holding.getQuantity()))
                        .add(cost)
                        .divide(BigDecimal.valueOf(newQuantity), RoundingMode.HALF_UP);

        holding.setQuantity(newQuantity);
        holding.setAvgCost(newAvgCost);
        holdingRepository.save(holding);

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setStock(stock);
        transaction.setType(TransactionType.BUY);
        transaction.setQuantity(request.quantity());
        transaction.setTimestamp(Instant.now());
        transaction.setPrice(price);

        Transaction savedTransaction = transactionRepository.save(transaction);

        return TransactionMapper.toDto(savedTransaction);
    }

    @Transactional
    @Audited(action = "SELL_STOCK", entityType = "TRANSACTION")
    public TransactionResponseDto sell(String email, SellRequestDto request) {

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        Stock stock = stockRepository.findBySymbol(request.symbol()).orElseThrow(
                () -> new StockNotFoundException("Stock not found")
        );

        Holding holding = holdingRepository.findByUserAndStock(user, stock).orElseThrow(
                () -> new InsufficientHoldingException("Insufficient holding " + request.quantity() + " " + request.symbol())
        );

        if (holding.getQuantity() < request.quantity()) {
            throw new InsufficientHoldingException("Insufficient holding " + request.quantity() + " " + request.symbol());
        }

        BigDecimal price = stockService.getPrice(request.symbol()).lastPrice();
        BigDecimal proceeds = price.multiply(BigDecimal.valueOf(request.quantity()));

        Wallet wallet = walletRepository.findByUser(user).orElseThrow(
                () -> new IllegalStateException("Wallet not found for user " + user.getId())
        );

        wallet.setBalance(wallet.getBalance().add(proceeds));
        walletRepository.save(wallet);

        long newQuantity = holding.getQuantity() - request.quantity();

        if (newQuantity == 0) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(newQuantity);
            holdingRepository.save(holding);
        }

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setStock(stock);
        transaction.setType(TransactionType.SELL);
        transaction.setQuantity(request.quantity());
        transaction.setTimestamp(Instant.now());
        transaction.setPrice(price);

        Transaction savedTransaction = transactionRepository.save(transaction);

        return TransactionMapper.toDto(savedTransaction);
    }

    public PagedModel<TransactionResponseDto> findAllTransactions(String email, Pageable pageable) {

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        return new PagedModel<>(
                transactionRepository
                        .findByUser(user, pageable)
                        .map(TransactionMapper::toDto)
        );
    }

    public TransactionResponseDto findTransaction(String email, @PathVariable Long id) {
        Transaction t = transactionRepository.findById(id).orElseThrow(
                () -> new TransactionNotFoundException("Transaction not found")
        );

        if (!t.getUser().getEmail().equals(email)) {
            throw new TransactionNotFoundException("Transaction not found");
        }

        return TransactionMapper.toDto(t);
    }
}
