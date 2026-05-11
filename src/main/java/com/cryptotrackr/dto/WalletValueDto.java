package com.cryptotrackr.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletValueDto(
        Long walletId,
        String walletName,
        BigDecimal totalValueUsd,
        List<AssetValueDto> assets
) {}
