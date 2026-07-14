package com.serphenix.portfolio.transaction.service;

import com.serphenix.portfolio.audit.Audited;
import com.serphenix.portfolio.client.PortfolioClient;
import com.serphenix.portfolio.client.StockClient;
import com.serphenix.portfolio.client.WalletClient;
import com.serphenix.portfolio.client.dto.HoldingBuyRequestDto;
import com.serphenix.portfolio.client.dto.HoldingSellRequestDto;
import com.serphenix.portfolio.client.dto.StockResponseDto;
import com.serphenix.portfolio.client.dto.WalletRequestDto;
import com.serphenix.portfolio.helper.CompensationHelper;
import com.serphenix.portfolio.transaction.dto.request.BuyRequestDto;
import com.serphenix.portfolio.transaction.dto.request.SellRequestDto;
import com.serphenix.portfolio.transaction.dto.response.TransactionResponseDto;
import com.serphenix.portfolio.transaction.entity.Transaction;
import com.serphenix.portfolio.transaction.entity.enums.TransactionType;
import com.serphenix.portfolio.transaction.event.TransactionEvent;
import com.serphenix.portfolio.transaction.exception.StockNotFoundException;
import com.serphenix.portfolio.transaction.exception.TransactionNotFoundException;
import com.serphenix.portfolio.transaction.mapper.TransactionMapper;
import com.serphenix.portfolio.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StockClient stockClient;
    private final WalletClient walletClient;
    private final PortfolioClient portfolioClient;

    @Audited(action = "BUY_STOCK", entityType = "TRANSACTION")
    public TransactionResponseDto buy(Long userId, BuyRequestDto request) {

        StockResponseDto stock = stockClient.getBySymbol(request.symbol());
        BigDecimal price = stock.lastPrice();
        BigDecimal cost = price.multiply(BigDecimal.valueOf(request.quantity()));

        walletClient.withdraw(new WalletRequestDto(cost));

        try {
            portfolioClient.applyBuy(new HoldingBuyRequestDto(
                    request.symbol(),
                    request.quantity(),
                    price
            ));
        } catch (RestClientException e) {
            log.error("Failed to apply buy holding for user {}, compensating wallet withdrawal", userId, e);
            CompensationHelper.compensate(() -> walletClient.deposit(new WalletRequestDto(cost)),
                    "refund withdrawal of " + cost + " for user " + userId);
            throw e;
        }

        Transaction transaction = Transaction.create(
                userId,
                stock.id(),
                TransactionType.BUY,
                request.quantity(),
                price
        );

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("user {} bought {} {} @ {}", userId, request.quantity(), request.symbol(), price);

        kafkaTemplate.send("transaction-events", new TransactionEvent(
                userId,
                transaction.getType(),
                stock.symbol(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getTimestamp()
        ));

        return TransactionMapper.toDto(savedTransaction, stock.symbol());
    }

    @Audited(action = "SELL_STOCK", entityType = "TRANSACTION")
    public TransactionResponseDto sell(Long userId, SellRequestDto request) {

        StockResponseDto stock = stockClient.getBySymbol(request.symbol());
        BigDecimal price = stock.lastPrice();
        BigDecimal proceeds = price.multiply(BigDecimal.valueOf(request.quantity()));

        walletClient.deposit(new WalletRequestDto(proceeds));

        try {
            portfolioClient.applySell(new HoldingSellRequestDto(
                    request.symbol(),
                    request.quantity()
            ));
        } catch (RestClientException e) {
            log.error("Failed to deposit proceeds for user {}, compensating holding sell", userId, e);
            CompensationHelper.compensate(() -> walletClient.withdraw(new WalletRequestDto(proceeds)),
                    "restore " + request.quantity() + " " + request.symbol() + " for user " + userId);
            throw e;
        }

        Transaction transaction = Transaction.create(
                userId,
                stock.id(),
                TransactionType.SELL,
                request.quantity(),
                price
        );

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("user {} sold {} {} @ {}", userId, request.quantity(), request.symbol(), price);

        kafkaTemplate.send("transaction-events", new TransactionEvent(
                userId,
                transaction.getType(),
                stock.symbol(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getTimestamp()
        ));

        return TransactionMapper.toDto(savedTransaction, stock.symbol());
    }

    public PagedModel<TransactionResponseDto> findAllTransactions(Long userId, Pageable pageable) {

        Page<Transaction> page = transactionRepository.findByUserId(userId, pageable);

        Map<Long, String> symbolsByStockId = stockClient
                .getByIds(page.getContent().stream().map(Transaction::getStockId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(StockResponseDto::id, StockResponseDto::symbol));

        return new PagedModel<>(
                page.map(t -> TransactionMapper.toDto(t, symbolsByStockId.get(t.getStockId())))
        );
    }

    public TransactionResponseDto findTransaction(Long userId, Long id) {
        Transaction t = transactionRepository.findById(id).orElseThrow(
                () -> new TransactionNotFoundException("Transaction not found")
        );

        if (!t.getUserId().equals(userId)) {
            throw new TransactionNotFoundException("Transaction not found");
        }

        StockResponseDto stock = stockClient.getByIds(List.of(t.getStockId())).stream().findFirst().orElseThrow(
                () -> new StockNotFoundException("Stock not found")
        );

        return TransactionMapper.toDto(t, stock.symbol());
    }
}
