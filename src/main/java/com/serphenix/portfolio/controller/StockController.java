package com.serphenix.portfolio.controller;

import com.serphenix.portfolio.dto.response.StockResponseDto;
import com.serphenix.portfolio.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {
    private final StockService stockService;

    @GetMapping
    public PagedModel<StockResponseDto> findAll(@RequestParam(name = "s", required = false) String search, Pageable pageable) {
        return stockService.findAll(search, pageable);
    }

    @GetMapping("/{symbol}")
    public StockResponseDto findBySymbol(@PathVariable String symbol) {
        return stockService.getPrice(symbol);
    }
}
