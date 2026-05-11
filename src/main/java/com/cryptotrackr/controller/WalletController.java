package com.cryptotrackr.controller;

import com.cryptotrackr.dto.request.AddAssetRequest;
import com.cryptotrackr.dto.request.CreateWalletRequest;
import com.cryptotrackr.dto.response.WalletAssetResponse;
import com.cryptotrackr.dto.response.WalletResponse;
import com.cryptotrackr.domain.Wallet;
import com.cryptotrackr.domain.WalletAsset;
import com.cryptotrackr.service.PerformanceService;
import com.cryptotrackr.service.WalletService;
import com.cryptotrackr.dto.WalletPerformanceDto;
import com.cryptotrackr.dto.WalletValueDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final PerformanceService performanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(@Valid @RequestBody CreateWalletRequest request) {
        Wallet wallet = walletService.createWallet(request.name(), request.userId());
        return new WalletResponse(wallet.getId(), wallet.getName(), wallet.getCreatedAt());
    }

    @PostMapping("/{id}/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public WalletAssetResponse addAsset(@PathVariable Long id, @Valid @RequestBody AddAssetRequest request) {
        WalletAsset wa = walletService.addAsset(id, request.symbol(), request.name(), request.code(),
                request.quantity(), request.purchasePrice(), request.purchaseDate());
        return new WalletAssetResponse(wa.getId(), wa.getAsset().getSymbol(), wa.getAsset().getName(),
                wa.getQuantity(), wa.getPurchasePrice(), wa.getPurchaseDate());
    }

    @GetMapping("/{id}")
    public WalletValueDto getWalletValue(@PathVariable Long id) {
        return walletService.calculateWalletValue(id);
    }

    @GetMapping("/{id}/value")
    public WalletValueDto getHistoricalWalletValue(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate at) {
        return walletService.calculateWalletValueAt(id, at);
    }

    @GetMapping("/{id}/performance")
    public WalletPerformanceDto getPerformance(
            @PathVariable Long id,
            @RequestParam(defaultValue = "24") int windowHours) {
        return performanceService.calculatePerformance(id, windowHours);
    }
}
