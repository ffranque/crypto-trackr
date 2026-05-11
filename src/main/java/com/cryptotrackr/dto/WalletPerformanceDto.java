package com.cryptotrackr.dto;

import java.util.List;

public record WalletPerformanceDto(
        AssetPerformanceDto best,
        AssetPerformanceDto worst,
        List<AssetPerformanceDto> all
) {}
