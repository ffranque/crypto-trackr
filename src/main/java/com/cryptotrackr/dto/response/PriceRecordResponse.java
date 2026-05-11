package com.cryptotrackr.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceRecordResponse(
        Long id,
        BigDecimal priceUsd,
        LocalDateTime recordedAt
) {}
