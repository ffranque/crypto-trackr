package com.cryptotrackr.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LatestPriceCache {

    private final ConcurrentHashMap<String, BigDecimal> prices = new ConcurrentHashMap<>();

    public void put(String symbol, BigDecimal price) {
        prices.put(symbol, price);
    }

    public Optional<BigDecimal> get(String symbol) {
        return Optional.ofNullable(prices.get(symbol));
    }
}
