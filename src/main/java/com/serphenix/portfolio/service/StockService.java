package com.serphenix.portfolio.service;

import com.serphenix.portfolio.dto.response.StockResponseDto;
import com.serphenix.portfolio.mapper.StockMapper;
import com.serphenix.portfolio.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockRepository stockRepository;

    public List<StockResponseDto> findAll() {
        return stockRepository.findAll().stream()
                .map(StockMapper::toDto)
                .toList();
    }
}
