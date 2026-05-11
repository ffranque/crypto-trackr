package com.cryptotrackr.service;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.domain.PriceRecord;
import com.cryptotrackr.repository.PriceRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
public class AssetPriceFetcher {

    private final CoinCapGateway coinCapGateway;
    private final PriceRecordRepository priceRecordRepository;
    private final LatestPriceCache latestPriceCache;
    private final Semaphore fetchSemaphore;

    public AssetPriceFetcher(CoinCapGateway coinCapGateway,
                             PriceRecordRepository priceRecordRepository,
                             LatestPriceCache latestPriceCache,
                             @Value("${coincap.max-concurrent-fetches:5}") int maxConcurrentFetches) {
        this.coinCapGateway = coinCapGateway;
        this.priceRecordRepository = priceRecordRepository;
        this.latestPriceCache = latestPriceCache;
        this.fetchSemaphore = new Semaphore(maxConcurrentFetches);
    }

    public boolean fetchAndSave(Asset asset) {
        try {
            fetchSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting to fetch asset '{}', skipping", asset.getSymbol());
            return false;
        }
        try {
            var price = coinCapGateway.fetchPrice(asset.getSymbol());
            if (price.isPresent()) {
                priceRecordRepository.save(PriceRecord.builder()
                        .asset(asset)
                        .priceUsd(price.get())
                        .recordedAt(LocalDateTime.now())
                        .build());
                latestPriceCache.put(asset.getSymbol(), price.get());
                return true;
            }
            log.warn("No price returned for asset '{}'", asset.getSymbol());
            return false;
        } catch (Exception e) {
            log.warn("Error processing asset '{}': {}", asset.getSymbol(), e.getMessage());
            return false;
        } finally {
            fetchSemaphore.release();
        }
    }
}
