package com.cryptotrackr.infrastructure;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceFetchScheduler {

    private final AssetPriceFetcher assetPriceFetcher;
    private final AssetRepository assetRepository;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Scheduled(fixedRateString = "${coincap.fetch-interval-ms}")
    public void fetchPrices() {
        List<Asset> assets;
        try {
            assets = assetRepository.findAll();
        } catch (Exception e) {
            log.error("Failed to load assets, skipping fetch cycle: {}", e.getMessage());
            return;
        }

        log.info("Starting price fetch for {} assets", assets.size());
        if (assets.isEmpty()) return;

        List<CompletableFuture<Boolean>> futures = assets.stream()
                .map(asset -> CompletableFuture.supplyAsync(() -> assetPriceFetcher.fetchAndSave(asset), executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    long saved = futures.stream().filter(f -> Boolean.TRUE.equals(f.getNow(false))).count();
                    log.info("Price fetch complete: {}/{} prices saved", saved, assets.size());
                })
                .exceptionally(ex -> {
                    log.error("Unexpected error in price fetch cycle: {}", ex.getMessage());
                    return null;
                });
    }
}
