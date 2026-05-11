package com.cryptotrackr.infrastructure;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceFetchSchedulerTest {

    @Mock private AssetPriceFetcher assetPriceFetcher;
    @Mock private AssetRepository assetRepository;

    @InjectMocks private PriceFetchScheduler scheduler;

    private Asset asset(long id, String symbol) {
        return Asset.builder().id(id).symbol(symbol).name(symbol).code(symbol.toUpperCase()).build();
    }

    @Test
    void shouldDispatchOneFetchPerAsset() {
        Asset bitcoin  = asset(1L, "bitcoin");
        Asset ethereum = asset(2L, "ethereum");
        when(assetRepository.findAll()).thenReturn(List.of(bitcoin, ethereum));
        when(assetPriceFetcher.fetchAndSave(any())).thenReturn(true);

        scheduler.fetchPrices();

        verify(assetPriceFetcher, timeout(2000)).fetchAndSave(bitcoin);
        verify(assetPriceFetcher, timeout(2000)).fetchAndSave(ethereum);
    }

    @Test
    void shouldDoNothingWhenNoAssetsRegistered() {
        when(assetRepository.findAll()).thenReturn(List.of());

        scheduler.fetchPrices();

        verify(assetPriceFetcher, never()).fetchAndSave(any());
    }

    @Test
    void shouldSkipFetchWhenDatabaseLoadFails() {
        when(assetRepository.findAll()).thenThrow(new RuntimeException("connection refused"));

        scheduler.fetchPrices();

        verify(assetPriceFetcher, never()).fetchAndSave(any());
    }

    @Test
    void shouldFetchAssetsInParallel() throws InterruptedException {
        CountDownLatch allStarted = new CountDownLatch(2);
        CountDownLatch proceed    = new CountDownLatch(1);

        when(assetRepository.findAll()).thenReturn(List.of(asset(1L, "bitcoin"), asset(2L, "ethereum")));
        when(assetPriceFetcher.fetchAndSave(any())).thenAnswer(inv -> {
            allStarted.countDown();
            proceed.await();
            return true;
        });

        scheduler.fetchPrices();

        assertThat(allStarted.await(2, TimeUnit.SECONDS))
                .as("both fetch tasks must be running concurrently before the latch opens")
                .isTrue();
        proceed.countDown();
    }
}
