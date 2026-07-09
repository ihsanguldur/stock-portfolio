package com.serphenix.portfolio.controller;

import com.serphenix.portfolio.dto.StockResponseDto;
import com.serphenix.portfolio.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {
    private final StockService stockService;

    @GetMapping
    public List<StockResponseDto> findAll() {
        return stockService.findAll();
    }
}
