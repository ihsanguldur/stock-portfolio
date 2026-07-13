package com.serphenix.portfolio.transaction.mapper;

import com.serphenix.portfolio.transaction.dto.response.TransactionResponseDto;
import com.serphenix.portfolio.transaction.entity.Transaction;

public class TransactionMapper {
    public static TransactionResponseDto toDto(Transaction transaction, String symbol) {
        return new TransactionResponseDto(
                transaction.getId(),
                symbol,
                transaction.getType(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getTimestamp()
        );
    }
}
