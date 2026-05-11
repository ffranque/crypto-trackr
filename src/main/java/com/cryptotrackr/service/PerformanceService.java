package com.cryptotrackr.service;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.domain.PriceRecord;
import com.cryptotrackr.domain.WalletAsset;
import com.cryptotrackr.repository.PriceRecordRepository;
import com.cryptotrackr.repository.WalletAssetRepository;
import com.cryptotrackr.repository.WalletRepository;
import com.cryptotrackr.dto.AssetPerformanceDto;
import com.cryptotrackr.dto.WalletPerformanceDto;
import com.cryptotrackr.infrastructure.LotAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final WalletRepository walletRepository;
    private final WalletAssetRepository walletAssetRepository;
    private final PriceRecordRepository priceRecordRepository;
    private final LotAggregator lotAggregator;

    public WalletPerformanceDto calculatePerformance(Long walletId, int windowHours) {
        var wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found: " + walletId));

        List<WalletAsset> lots = walletAssetRepository.findByWallet(wallet);
        var grouped = lotAggregator.aggregate(lots);

        List<AssetPerformanceDto> results = new ArrayList<>();
        LocalDateTime windowStart = LocalDateTime.now().minusHours(windowHours);

        for (String symbol : grouped.quantityBySymbol().keySet()) {
            Asset asset = grouped.assetBySymbol().get(symbol);

            var latestPrice = priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(asset);
            if (latestPrice.isEmpty()) {
                log.warn("No current price for asset '{}', skipping", symbol);
                continue;
            }

            List<PriceRecord> history = priceRecordRepository
                    .findByAssetAndRecordedAtAfter(asset, windowStart);
            if (history.isEmpty()) {
                log.warn("No price history in window for asset '{}', skipping", symbol);
                continue;
            }

            BigDecimal priceNow = latestPrice.get().getPriceUsd();
            BigDecimal priceThen = history.stream()
                    .min(Comparator.comparing(PriceRecord::getRecordedAt))
                    .orElseThrow()
                    .getPriceUsd();

            BigDecimal changePercent = priceNow.subtract(priceThen)
                    .divide(priceThen, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            results.add(new AssetPerformanceDto(symbol, asset.getName(), asset.getCode(),
                    grouped.quantityBySymbol().get(symbol), priceNow, priceThen, changePercent));
        }

        results.sort(Comparator.comparing(AssetPerformanceDto::changePercent).reversed());

        AssetPerformanceDto best  = results.isEmpty() ? null : results.get(0);
        AssetPerformanceDto worst = results.isEmpty() ? null : results.get(results.size() - 1);

        return new WalletPerformanceDto(best, worst, results);
    }
}
