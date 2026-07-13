package com.serphenix.portfolio.transaction.service;

import com.serphenix.portfolio.audit.Audited;
import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.auth.repository.UserRepository;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import com.serphenix.portfolio.portfolio.entity.Holding;
import com.serphenix.portfolio.portfolio.exception.InsufficientHoldingException;
import com.serphenix.portfolio.portfolio.repository.HoldingRepository;
import com.serphenix.portfolio.stock.entity.Stock;
import com.serphenix.portfolio.stock.exception.StockNotFoundException;
import com.serphenix.portfolio.stock.repository.StockRepository;
import com.serphenix.portfolio.stock.service.StockService;
import com.serphenix.portfolio.transaction.dto.request.BuyRequestDto;
import com.serphenix.portfolio.transaction.dto.request.SellRequestDto;
import com.serphenix.portfolio.transaction.dto.response.TransactionResponseDto;
import com.serphenix.portfolio.transaction.entity.Transaction;
import com.serphenix.portfolio.transaction.entity.enums.TransactionType;
import com.serphenix.portfolio.transaction.event.TransactionEvent;
import com.serphenix.portfolio.transaction.exception.TransactionNotFoundException;
import com.serphenix.portfolio.transaction.mapper.TransactionMapper;
import com.serphenix.portfolio.transaction.repository.TransactionRepository;
import com.serphenix.portfolio.wallet.entity.Wallet;
import com.serphenix.portfolio.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;
    private final WalletRepository walletRepository;
    private final HoldingRepository holdingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

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

        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow(
                () -> new IllegalStateException("Wallet not found for user " + user.getId())
        );

        wallet.withdraw(cost);
        walletRepository.save(wallet);

        Holding holding = holdingRepository.findByUserIdAndStockId(user.getId(), stock.getId()).orElseGet(
                () -> Holding.create(user.getId(), stock.getId())
        );

        holding.applyBuy(request.quantity(), price, cost);

        holdingRepository.save(holding);

        Transaction transaction = Transaction.create(
                user.getId(),
                stock.getId(),
                TransactionType.BUY,
                request.quantity(),
                price
        );

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("{} bought {} {} @ {}", email, request.quantity(), request.symbol(), price);

        kafkaTemplate.send("transaction-events", new TransactionEvent(
                user.getEmail(),
                transaction.getType(),
                stock.getSymbol(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getTimestamp()
        ));

        return TransactionMapper.toDto(savedTransaction, stock.getSymbol());
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

        Holding holding = holdingRepository.findByUserIdAndStockId(user.getId(), stock.getId()).orElseThrow(
                () -> {
                    log.warn("Insufficient holding for {} to sell {} {}", email, request.quantity(), request.symbol());
                    return new InsufficientHoldingException("Insufficient holding " + request.quantity() + " " + request.symbol());
                }
        );

        holding.applySell(request.quantity(), stock.getSymbol());

        BigDecimal price = stockService.getPrice(request.symbol()).lastPrice();
        BigDecimal proceeds = price.multiply(BigDecimal.valueOf(request.quantity()));

        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow(
                () -> new IllegalStateException("Wallet not found for user " + user.getId())
        );

        wallet.deposit(proceeds);

        walletRepository.save(wallet);

        if (holding.isEmpty()) {
            holdingRepository.delete(holding);
        } else {
            holdingRepository.save(holding);
        }

        Transaction transaction = Transaction.create(
                user.getId(),
                stock.getId(),
                TransactionType.SELL,
                request.quantity(),
                price
        );

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("{} sold {} {} @ {}", email, request.quantity(), request.symbol(), price);

        kafkaTemplate.send("transaction-events", new TransactionEvent(
                user.getEmail(),
                transaction.getType(),
                stock.getSymbol(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getTimestamp()
        ));

        return TransactionMapper.toDto(savedTransaction, stock.getSymbol());
    }

    public PagedModel<TransactionResponseDto> findAllTransactions(String email, Pageable pageable) {

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        Page<Transaction> page = transactionRepository.findByUserId(user.getId(), pageable);

        Map<Long, String> symbolsByStockId = stockRepository
                .findAllById(page.getContent().stream().map(Transaction::getStockId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Stock::getId, Stock::getSymbol));

        return new PagedModel<>(
                page.map(t -> TransactionMapper.toDto(t, symbolsByStockId.get(t.getStockId())))
        );
    }

    public TransactionResponseDto findTransaction(String email, @PathVariable Long id) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new InvalidCredentialsException("User not found")
        );

        Transaction t = transactionRepository.findById(id).orElseThrow(
                () -> new TransactionNotFoundException("Transaction not found")
        );

        Stock stock = stockRepository.findById(t.getStockId()).orElseThrow(
                () -> new StockNotFoundException("Stock not found")
        );

        if (!t.getUserId().equals(user.getId())) {
            throw new TransactionNotFoundException("Transaction not found");
        }

        return TransactionMapper.toDto(t, stock.getSymbol());
    }
}
