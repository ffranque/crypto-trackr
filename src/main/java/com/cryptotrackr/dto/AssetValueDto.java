package com.cryptotrackr.dto;

import java.math.BigDecimal;

public record AssetValueDto(
        String symbol,
        String name,
        String code,
        BigDecimal quantity,
        BigDecimal priceUsd,
        BigDecimal totalValueUsd
) {}
