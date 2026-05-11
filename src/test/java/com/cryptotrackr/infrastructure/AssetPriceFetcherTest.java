package com.cryptotrackr.infrastructure;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.domain.PriceRecord;
import com.cryptotrackr.repository.PriceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetPriceFetcherTest {

    @Mock private CoinCapGateway coinCapGateway;
    @Mock private PriceRecordRepository priceRecordRepository;
    @Mock private LatestPriceCache latestPriceCache;

    private AssetPriceFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new AssetPriceFetcher(coinCapGateway, priceRecordRepository, latestPriceCache, 10);
    }

    private Asset asset(long id, String symbol) {
        return Asset.builder().id(id).symbol(symbol).name(symbol).code(symbol.toUpperCase()).build();
    }

    @Test
    void shouldPersistRecordAndUpdateCacheWhenPriceFetched() {
        Asset bitcoin = asset(1L, "bitcoin");
        when(coinCapGateway.fetchPrice("bitcoin")).thenReturn(Optional.of(new BigDecimal("67000.00")));

        boolean result = fetcher.fetchAndSave(bitcoin);

        assertThat(result).isTrue();
        ArgumentCaptor<PriceRecord> captor = ArgumentCaptor.forClass(PriceRecord.class);
        verify(priceRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getPriceUsd()).isEqualByComparingTo("67000.00");
        assertThat(captor.getValue().getAsset()).isEqualTo(bitcoin);
        verify(latestPriceCache).put("bitcoin", new BigDecimal("67000.00"));
    }

    @Test
    void shouldReturnFalseWhenProviderReturnsEmpty() {
        Asset bitcoin = asset(1L, "bitcoin");
        when(coinCapGateway.fetchPrice("bitcoin")).thenReturn(Optional.empty());

        boolean result = fetcher.fetchAndSave(bitcoin);

        assertThat(result).isFalse();
        verify(priceRecordRepository, never()).save(any());
    }

    @Test
    void shouldReturnFalseWhenProviderThrows() {
        Asset bitcoin = asset(1L, "bitcoin");
        when(coinCapGateway.fetchPrice("bitcoin")).thenThrow(new RuntimeException("timeout"));

        boolean result = fetcher.fetchAndSave(bitcoin);

        assertThat(result).isFalse();
        verify(priceRecordRepository, never()).save(any());
    }

    @Test
    void shouldNotUpdateCacheWhenPriceIsEmpty() {
        Asset bitcoin = asset(1L, "bitcoin");
        when(coinCapGateway.fetchPrice("bitcoin")).thenReturn(Optional.empty());

        fetcher.fetchAndSave(bitcoin);

        verify(latestPriceCache, never()).put(any(), any());
    }

    @Test
    void shouldBlockSecondFetchUntilFirstCompletes() throws InterruptedException {
        AssetPriceFetcher throttled = new AssetPriceFetcher(
                coinCapGateway, priceRecordRepository, latestPriceCache, 1);

        Asset bitcoin  = asset(1L, "bitcoin");
        Asset ethereum = asset(2L, "ethereum");

        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirst  = new CountDownLatch(1);
        AtomicBoolean secondStarted  = new AtomicBoolean(false);

        when(coinCapGateway.fetchPrice("bitcoin")).thenAnswer(inv -> {
            firstAcquired.countDown();
            releaseFirst.await();
            return Optional.of(new BigDecimal("67000.00"));
        });
        when(coinCapGateway.fetchPrice("ethereum")).thenAnswer(inv -> {
            secondStarted.set(true);
            return Optional.of(new BigDecimal("3500.00"));
        });

        Thread t1 = new Thread(() -> throttled.fetchAndSave(bitcoin));
        Thread t2 = new Thread(() -> throttled.fetchAndSave(ethereum));
        t1.start();
        assertThat(firstAcquired.await(2, TimeUnit.SECONDS)).isTrue();
        t2.start();

        Thread.sleep(100);
        assertThat(secondStarted.get())
                .as("ethereum must not reach the provider while bitcoin holds the semaphore permit")
                .isFalse();

        releaseFirst.countDown();
        t1.join(2000);
        t2.join(2000);
    }
}
