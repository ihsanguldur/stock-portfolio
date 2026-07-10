package com.serphenix.portfolio.mapper;

import com.serphenix.portfolio.dto.response.TransactionResponseDto;
import com.serphenix.portfolio.entity.Transaction;

public class TransactionMapper {
    public static TransactionResponseDto toDto(Transaction transaction) {
        return new TransactionResponseDto(
                transaction.getId(),
                transaction.getStock().getSymbol(),
                transaction.getType(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getTimestamp()
        );
    }
}
