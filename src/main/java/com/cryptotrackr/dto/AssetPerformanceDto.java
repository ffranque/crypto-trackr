package com.cryptotrackr.dto;

import java.math.BigDecimal;

public record AssetPerformanceDto(
        String symbol,
        String name,
        String code,
        BigDecimal quantity,
        BigDecimal priceUsdNow,
        BigDecimal priceUsdThen,
        BigDecimal changePercent
) {}
