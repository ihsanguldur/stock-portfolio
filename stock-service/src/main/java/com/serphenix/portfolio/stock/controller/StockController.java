package com.serphenix.portfolio.stock.controller;

import com.serphenix.portfolio.stock.dto.response.StockResponseDto;
import com.serphenix.portfolio.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {
    private final StockService stockService;

    @GetMapping
    public PagedModel<StockResponseDto> findAll(@RequestParam(name = "s", required = false) String search, Pageable pageable) {
        return stockService.findAll(search, pageable);
    }

    @GetMapping("/by-ids")
    public List<StockResponseDto> findByIds(@RequestParam List<Long> ids) {
        return stockService.findByIds(ids);
    }

    @GetMapping("/{symbol}")
    public StockResponseDto findBySymbol(@PathVariable String symbol) {
        return stockService.getPrice(symbol);
    }
}
