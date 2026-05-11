package com.cryptotrackr.service;

import com.cryptotrackr.domain.PriceRecord;
import com.cryptotrackr.repository.AssetRepository;
import com.cryptotrackr.repository.PriceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final PriceRecordRepository priceRecordRepository;

    public Page<PriceRecord> getPriceHistory(String symbol, int hours, Pageable pageable) {
        var asset = assetRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found: " + symbol));

        return priceRecordRepository.findByAssetAndRecordedAtAfter(
                asset, LocalDateTime.now().minusHours(hours), pageable);
    }
}
