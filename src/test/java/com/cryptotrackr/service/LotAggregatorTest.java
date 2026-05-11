package com.cryptotrackr.service;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.domain.Wallet;
import com.cryptotrackr.domain.WalletAsset;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LotAggregatorTest {

    private final LotAggregator aggregator = new LotAggregator();

    private Asset asset(long id) {
        return Asset.builder().id(id).symbol("bitcoin").name("bitcoin").code("bitcoin".toUpperCase()).build();
    }

    private WalletAsset lot(Asset a, String quantity) {
        return WalletAsset.builder()
                .wallet(Wallet.builder().id(1L).name("w").build())
                .asset(a)
                .quantity(new BigDecimal(quantity))
                .build();
    }

    @Test
    void shouldSumQuantitiesForSameSymbol() {
        Asset bitcoin = asset(1L);

        var result = aggregator.aggregate(List.of(lot(bitcoin, "1.0"), lot(bitcoin, "0.5")));

        assertThat(result.quantityBySymbol()).containsEntry("bitcoin", new BigDecimal("1.5"));
    }

    @Test
    void shouldKeepFirstAssetInstanceForDuplicateSymbol() {
        Asset btc1 = asset(1L);
        Asset btc2 = asset(99L);

        var result = aggregator.aggregate(List.of(lot(btc1, "1.0"), lot(btc2, "0.5")));

        assertThat(result.assetBySymbol().get("bitcoin")).isEqualTo(btc1);
    }

    @Test
    void shouldReturnEmptyMapsForEmptyInput() {
        var result = aggregator.aggregate(List.of());

        assertThat(result.quantityBySymbol()).isEmpty();
        assertThat(result.assetBySymbol()).isEmpty();
    }
}
