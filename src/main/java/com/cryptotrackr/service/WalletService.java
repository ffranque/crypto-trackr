package com.cryptotrackr.service;

import com.cryptotrackr.domain.*;
import com.cryptotrackr.repository.AssetRepository;
import com.cryptotrackr.repository.PriceRecordRepository;
import com.cryptotrackr.repository.UserRepository;
import com.cryptotrackr.repository.WalletAssetRepository;
import com.cryptotrackr.repository.WalletRepository;
import com.cryptotrackr.dto.AssetValueDto;
import com.cryptotrackr.dto.WalletValueDto;
import com.cryptotrackr.infrastructure.LatestPriceCache;
import com.cryptotrackr.infrastructure.LotAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletAssetRepository walletAssetRepository;
    private final PriceRecordRepository priceRecordRepository;
    private final LatestPriceCache latestPriceCache;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final LotAggregator lotAggregator;

    public Wallet createWallet(String name, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));

        return walletRepository.save(Wallet.builder()
                .name(name)
                .user(user)
                .build());
    }

    public WalletAsset addAsset(Long walletId, String symbol, String name, String code,
                                BigDecimal quantity, BigDecimal purchasePrice, LocalDate purchaseDate) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found: " + walletId));

        Asset asset = assetRepository.findBySymbol(symbol)
                .orElseGet(() -> assetRepository.save(Asset.builder()
                        .symbol(symbol)
                        .name(name)
                        .code(code)
                        .build()));

        return walletAssetRepository.save(WalletAsset.builder()
                .wallet(wallet)
                .asset(asset)
                .quantity(quantity)
                .purchasePrice(purchasePrice)
                .purchaseDate(purchaseDate)
                .build());
    }

    public WalletValueDto calculateWalletValue(Long walletId) {
        var wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found: " + walletId));

        List<WalletAsset> lots = walletAssetRepository.findByWallet(wallet);
        var grouped = lotAggregator.aggregate(lots);

        List<AssetValueDto> assetValues = new ArrayList<>();
        for (String symbol : grouped.quantityBySymbol().keySet()) {
            Asset asset = grouped.assetBySymbol().get(symbol);
            BigDecimal quantity = grouped.quantityBySymbol().get(symbol);
            BigDecimal priceUsd = latestPriceCache.get(symbol)
                    .orElseGet(() -> priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(asset)
                            .map(PriceRecord::getPriceUsd)
                            .orElse(null));
            if (priceUsd == null) {
                log.warn("No price record for asset '{}', skipping", symbol);
                continue;
            }
            assetValues.add(new AssetValueDto(symbol, asset.getName(), asset.getCode(), quantity, priceUsd,
                    quantity.multiply(priceUsd)));
        }

        BigDecimal total = assetValues.stream()
                .map(AssetValueDto::totalValueUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new WalletValueDto(wallet.getId(), wallet.getName(), total, assetValues);
    }

    public WalletValueDto calculateWalletValueAt(Long walletId, LocalDate date) {
        var wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found: " + walletId));

        List<WalletAsset> lots = walletAssetRepository.findByWallet(wallet).stream()
                .filter(wa -> wa.getPurchaseDate() != null && !wa.getPurchaseDate().isAfter(date))
                .toList();

        var grouped = lotAggregator.aggregate(lots);

        var asOf = date.atTime(23, 59, 59);
        List<AssetValueDto> assetValues = new ArrayList<>();
        for (String symbol : grouped.quantityBySymbol().keySet()) {
            Asset asset = grouped.assetBySymbol().get(symbol);
            BigDecimal quantity = grouped.quantityBySymbol().get(symbol);
            var priceAt = priceRecordRepository
                    .findTopByAssetAndRecordedAtLessThanEqualOrderByRecordedAtDesc(asset, asOf);
            if (priceAt.isEmpty()) {
                log.warn("No price record at {} for asset '{}', skipping", date, symbol);
                continue;
            }
            BigDecimal priceUsd = priceAt.get().getPriceUsd();
            assetValues.add(new AssetValueDto(symbol, asset.getName(), asset.getCode(), quantity, priceUsd,
                    quantity.multiply(priceUsd)));
        }

        BigDecimal total = assetValues.stream()
                .map(AssetValueDto::totalValueUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new WalletValueDto(wallet.getId(), wallet.getName(), total, assetValues);
    }
}
