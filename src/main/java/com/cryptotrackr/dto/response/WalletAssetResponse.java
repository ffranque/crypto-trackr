package com.cryptotrackr.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WalletAssetResponse(
        Long id,
        String symbol,
        String name,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        LocalDate purchaseDate
) {}
