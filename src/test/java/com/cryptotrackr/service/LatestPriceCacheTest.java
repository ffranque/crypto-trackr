package com.cryptotrackr.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LatestPriceCacheTest {

    private final LatestPriceCache cache = new LatestPriceCache();

    @Test
    void shouldReturnEmptyWhenCacheNotPopulated() {
        assertThat(cache.get("bitcoin")).isEmpty();
    }

    @Test
    void shouldReturnPriceAfterPut() {
        cache.put("bitcoin", new BigDecimal("67000.00"));
        assertThat(cache.get("bitcoin")).contains(new BigDecimal("67000.00"));
    }

    @Test
    void shouldOverwritePreviousValueOnPut() {
        cache.put("bitcoin", new BigDecimal("60000.00"));
        cache.put("bitcoin", new BigDecimal("67000.00"));
        assertThat(cache.get("bitcoin")).contains(new BigDecimal("67000.00"));
    }

    @Test
    void shouldReturnEmptyForUnknownSymbol() {
        cache.put("bitcoin", new BigDecimal("67000.00"));
        assertThat(cache.get("ethereum")).isEmpty();
    }
}
