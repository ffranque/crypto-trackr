package com.cryptotrackr.service;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.domain.WalletAsset;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LotAggregator {

    public record AggregatedLots(Map<String, BigDecimal> quantityBySymbol, Map<String, Asset> assetBySymbol) {}

    public AggregatedLots aggregate(List<WalletAsset> lots) {
        Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        Map<String, Asset> assets = new LinkedHashMap<>();
        for (WalletAsset wa : lots) {
            String symbol = wa.getAsset().getSymbol();
            quantities.merge(symbol, wa.getQuantity(), BigDecimal::add);
            assets.putIfAbsent(symbol, wa.getAsset());
        }
        return new AggregatedLots(quantities, assets);
    }
}
