package com.cryptotrackr.service;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.domain.PriceRecord;
import com.cryptotrackr.domain.User;
import com.cryptotrackr.domain.Wallet;
import com.cryptotrackr.domain.WalletAsset;
import com.cryptotrackr.repository.AssetRepository;
import com.cryptotrackr.repository.PriceRecordRepository;
import com.cryptotrackr.repository.UserRepository;
import com.cryptotrackr.repository.WalletAssetRepository;
import com.cryptotrackr.repository.WalletRepository;
import com.cryptotrackr.dto.WalletValueDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletAssetRepository walletAssetRepository;
    @Mock private PriceRecordRepository priceRecordRepository;
    @Mock private LatestPriceCache latestPriceCache;
    @Mock private UserRepository userRepository;
    @Mock private AssetRepository assetRepository;
    @Spy  private LotAggregator lotAggregator;

    @InjectMocks private WalletService walletService;

    private Wallet wallet(long id) {
        return Wallet.builder().id(id).name("Test Wallet").build();
    }

    private Asset asset(long id, String symbol, String name) {
        return Asset.builder().id(id).symbol(symbol).name(name).code(symbol.toUpperCase()).build();
    }

    private WalletAsset lot(Wallet w, Asset a, String quantity, LocalDate purchaseDate) {
        return WalletAsset.builder().wallet(w).asset(a)
                .quantity(new BigDecimal(quantity))
                .purchaseDate(purchaseDate).build();
    }

    private PriceRecord priceRecord(Asset a, String price) {
        return PriceRecord.builder().asset(a).priceUsd(new BigDecimal(price))
                .recordedAt(LocalDateTime.now()).build();
    }

    @Test
    void shouldSaveWalletWhenUserExists() {
        User user = User.builder().id(1L).username("alice").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletRepository.save(any())).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            return Wallet.builder().id(10L).name(w.getName()).user(w.getUser()).build();
        });

        Wallet result = walletService.createWallet("My Wallet", 1L);

        assertThat(result.getName()).isEqualTo("My Wallet");
        assertThat(result.getUser()).isEqualTo(user);
    }

    @Test
    void shouldThrowNotFoundWhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.createWallet("My Wallet", 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldReuseExistingAssetWhenSymbolMatches() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findBySymbol("bitcoin")).thenReturn(Optional.of(bitcoin));
        when(walletAssetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletAsset result = walletService.addAsset(1L, "bitcoin", "Bitcoin", "BTC",
                new BigDecimal("1.5"), new BigDecimal("45000"), LocalDate.of(2024, 1, 15));

        assertThat(result.getAsset()).isEqualTo(bitcoin);
        assertThat(result.getQuantity()).isEqualByComparingTo("1.5");
        verify(assetRepository, never()).save(any());
    }

    @Test
    void shouldCreateAssetWhenNotExists() {
        Wallet wallet = wallet(1L);
        Asset newAsset = asset(2L, "solana", "Solana");
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findBySymbol("solana")).thenReturn(Optional.empty());
        when(assetRepository.save(any())).thenReturn(newAsset);
        when(walletAssetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletAsset result = walletService.addAsset(1L, "solana", "Solana", "SOL",
                new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2024, 1, 15));

        assertThat(result.getAsset().getSymbol()).isEqualTo("solana");
        verify(assetRepository).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenWalletMissingOnAddAsset() {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.addAsset(99L, "bitcoin", "Bitcoin", "BTC",
                BigDecimal.ONE, BigDecimal.ONE, LocalDate.now()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldReturnSumOfAssetValues() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");
        Asset ethereum = asset(2L, "ethereum", "Ethereum");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet))
                .thenReturn(List.of(lot(wallet, bitcoin, "2.0", null), lot(wallet, ethereum, "10.0", null)));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(bitcoin))
                .thenReturn(Optional.of(priceRecord(bitcoin, "60000")));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(ethereum))
                .thenReturn(Optional.of(priceRecord(ethereum, "3000")));

        WalletValueDto result = walletService.calculateWalletValue(1L);

        assertThat(result.totalValueUsd()).isEqualByComparingTo("150000");
        assertThat(result.assets()).hasSize(2);
        assertThat(result.assets()).extracting(a -> a.symbol())
                .containsExactlyInAnyOrder("bitcoin", "ethereum");
    }

    @Test
    void shouldAggregateMultipleLotsWhenCalculatingValue() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet)).thenReturn(List.of(
                lot(wallet, bitcoin, "1.0", null),
                lot(wallet, bitcoin, "0.5", null)));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(bitcoin))
                .thenReturn(Optional.of(priceRecord(bitcoin, "60000")));

        WalletValueDto result = walletService.calculateWalletValue(1L);

        assertThat(result.assets()).hasSize(1);
        assertThat(result.assets().get(0).quantity()).isEqualByComparingTo("1.5");
        assertThat(result.totalValueUsd()).isEqualByComparingTo("90000");
    }

    @Test
    void shouldSkipAssetWithNoPriceRecord() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");
        Asset ethereum = asset(2L, "ethereum", "Ethereum");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet))
                .thenReturn(List.of(lot(wallet, bitcoin, "2.0", null), lot(wallet, ethereum, "10.0", null)));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(bitcoin))
                .thenReturn(Optional.of(priceRecord(bitcoin, "60000")));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(ethereum))
                .thenReturn(Optional.empty());

        WalletValueDto result = walletService.calculateWalletValue(1L);

        assertThat(result.assets()).hasSize(1);
        assertThat(result.assets().get(0).symbol()).isEqualTo("bitcoin");
        assertThat(result.totalValueUsd()).isEqualByComparingTo("120000");
    }

    @Test
    void shouldThrowNotFoundWhenWalletMissingOnCalculateValue() {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.calculateWalletValue(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldUseCachedPriceWithoutHittingDatabase() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet))
                .thenReturn(List.of(lot(wallet, bitcoin, "1.0", null)));
        when(latestPriceCache.get("bitcoin")).thenReturn(Optional.of(new BigDecimal("67000.00")));

        WalletValueDto result = walletService.calculateWalletValue(1L);

        assertThat(result.totalValueUsd()).isEqualByComparingTo("67000.00");
        verify(priceRecordRepository, never()).findTopByAssetOrderByRecordedAtDesc(any());
    }

    @Test
    void shouldFallBackToDatabaseWhenCacheMiss() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet))
                .thenReturn(List.of(lot(wallet, bitcoin, "2.0", null)));
        when(latestPriceCache.get("bitcoin")).thenReturn(Optional.empty());
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(bitcoin))
                .thenReturn(Optional.of(priceRecord(bitcoin, "60000")));

        WalletValueDto result = walletService.calculateWalletValue(1L);

        assertThat(result.totalValueUsd()).isEqualByComparingTo("120000");
        verify(priceRecordRepository).findTopByAssetOrderByRecordedAtDesc(bitcoin);
    }

    @Test
    void shouldReturnHistoricalValueUsingPriceAtDate() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");
        LocalDate date = LocalDate.of(2024, 6, 1);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet))
                .thenReturn(List.of(lot(wallet, bitcoin, "2.0", LocalDate.of(2024, 1, 15))));
        when(priceRecordRepository.findTopByAssetAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                eq(bitcoin), any()))
                .thenReturn(Optional.of(priceRecord(bitcoin, "50000")));

        WalletValueDto result = walletService.calculateWalletValueAt(1L, date);

        assertThat(result.assets()).hasSize(1);
        assertThat(result.assets().get(0).priceUsd()).isEqualByComparingTo("50000");
        assertThat(result.totalValueUsd()).isEqualByComparingTo("100000");
    }

    @Test
    void shouldExcludeLotsAddedAfterRequestedDate() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");
        LocalDate date = LocalDate.of(2024, 3, 1);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet)).thenReturn(List.of(
                lot(wallet, bitcoin, "1.0", LocalDate.of(2024, 1, 1)),
                lot(wallet, bitcoin, "0.5", LocalDate.of(2024, 6, 1)))); // after date → excluded
        when(priceRecordRepository.findTopByAssetAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                eq(bitcoin), any()))
                .thenReturn(Optional.of(priceRecord(bitcoin, "40000")));

        WalletValueDto result = walletService.calculateWalletValueAt(1L, date);

        assertThat(result.assets()).hasSize(1);
        assertThat(result.assets().get(0).quantity()).isEqualByComparingTo("1.0");
        assertThat(result.totalValueUsd()).isEqualByComparingTo("40000");
    }

    @Test
    void shouldSkipAssetWithNoPriceAtRequestedDate() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");
        LocalDate date = LocalDate.of(2020, 1, 1);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet))
                .thenReturn(List.of(lot(wallet, bitcoin, "1.0", LocalDate.of(2019, 12, 1))));
        when(priceRecordRepository.findTopByAssetAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                eq(bitcoin), any()))
                .thenReturn(Optional.empty());

        WalletValueDto result = walletService.calculateWalletValueAt(1L, date);

        assertThat(result.assets()).isEmpty();
        assertThat(result.totalValueUsd()).isEqualByComparingTo("0");
    }

    @Test
    void shouldThrowNotFoundWhenWalletMissingOnHistoricalValue() {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.calculateWalletValueAt(99L, LocalDate.now()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
