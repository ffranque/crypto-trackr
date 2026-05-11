package com.cryptotrackr.service;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.domain.PriceRecord;
import com.cryptotrackr.domain.Wallet;
import com.cryptotrackr.domain.WalletAsset;
import com.cryptotrackr.repository.PriceRecordRepository;
import com.cryptotrackr.repository.WalletAssetRepository;
import com.cryptotrackr.repository.WalletRepository;
import com.cryptotrackr.dto.WalletPerformanceDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletAssetRepository walletAssetRepository;
    @Mock private PriceRecordRepository priceRecordRepository;
    @Spy  private LotAggregator lotAggregator;

    @InjectMocks private PerformanceService performanceService;

    private Wallet wallet(long id) {
        return Wallet.builder().id(id).name("Test Wallet").build();
    }

    private Asset asset(long id, String symbol, String name) {
        return Asset.builder().id(id).symbol(symbol).name(name).code(symbol.toUpperCase()).build();
    }

    private WalletAsset walletAsset(Wallet w, Asset a, String quantity) {
        return WalletAsset.builder().wallet(w).asset(a).quantity(new BigDecimal(quantity)).build();
    }

    private PriceRecord priceRecord(Asset a, String price, LocalDateTime at) {
        return PriceRecord.builder().asset(a).priceUsd(new BigDecimal(price)).recordedAt(at).build();
    }

    @Test
    void shouldReturnCorrectChangePercent() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet))
                .thenReturn(List.of(walletAsset(wallet, bitcoin, "1.0")));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(bitcoin))
                .thenReturn(Optional.of(priceRecord(bitcoin, "60000", LocalDateTime.now())));
        when(priceRecordRepository.findByAssetAndRecordedAtAfter(eq(bitcoin), any()))
                .thenReturn(List.of(priceRecord(bitcoin, "50000", LocalDateTime.now().minusHours(24))));

        WalletPerformanceDto result = performanceService.calculatePerformance(1L, 24);

        assertThat(result.all()).hasSize(1);
        assertThat(result.all().get(0).changePercent()).isEqualByComparingTo("20.00");
        assertThat(result.best().symbol()).isEqualTo("bitcoin");
        assertThat(result.worst().symbol()).isEqualTo("bitcoin");
    }

    @Test
    void shouldSortByChangePercentDescendingAndExposeBestWorst() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");
        Asset ethereum = asset(2L, "ethereum", "Ethereum");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet)).thenReturn(List.of(
                walletAsset(wallet, bitcoin, "1.0"),
                walletAsset(wallet, ethereum, "5.0")));
        // BTC: 50000 → 60000 = +20%
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(bitcoin))
                .thenReturn(Optional.of(priceRecord(bitcoin, "60000", LocalDateTime.now())));
        when(priceRecordRepository.findByAssetAndRecordedAtAfter(eq(bitcoin), any()))
                .thenReturn(List.of(priceRecord(bitcoin, "50000", LocalDateTime.now().minusHours(24))));
        // ETH: 3000 → 2850 = -5%
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(ethereum))
                .thenReturn(Optional.of(priceRecord(ethereum, "2850", LocalDateTime.now())));
        when(priceRecordRepository.findByAssetAndRecordedAtAfter(eq(ethereum), any()))
                .thenReturn(List.of(priceRecord(ethereum, "3000", LocalDateTime.now().minusHours(24))));

        WalletPerformanceDto result = performanceService.calculatePerformance(1L, 24);

        assertThat(result.all()).hasSize(2);
        assertThat(result.all().get(0).symbol()).isEqualTo("bitcoin");
        assertThat(result.all().get(1).symbol()).isEqualTo("ethereum");
        assertThat(result.best().symbol()).isEqualTo("bitcoin");
        assertThat(result.best().changePercent()).isEqualByComparingTo("20.00");
        assertThat(result.worst().symbol()).isEqualTo("ethereum");
        assertThat(result.worst().changePercent()).isEqualByComparingTo("-5.00");
    }

    @Test
    void shouldAggregateMultipleLotsOfSameAsset() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet)).thenReturn(List.of(
                walletAsset(wallet, bitcoin, "1.0"),
                walletAsset(wallet, bitcoin, "0.5")));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(bitcoin))
                .thenReturn(Optional.of(priceRecord(bitcoin, "60000", LocalDateTime.now())));
        when(priceRecordRepository.findByAssetAndRecordedAtAfter(eq(bitcoin), any()))
                .thenReturn(List.of(priceRecord(bitcoin, "50000", LocalDateTime.now().minusHours(24))));

        WalletPerformanceDto result = performanceService.calculatePerformance(1L, 24);

        assertThat(result.all()).hasSize(1);
        assertThat(result.all().get(0).quantity()).isEqualByComparingTo("1.5");
        assertThat(result.all().get(0).changePercent()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldSkipAssetWithInsufficientPriceHistory() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");
        Asset ethereum = asset(2L, "ethereum", "Ethereum");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet)).thenReturn(List.of(
                walletAsset(wallet, bitcoin, "1.0"),
                walletAsset(wallet, ethereum, "5.0")));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(bitcoin))
                .thenReturn(Optional.of(priceRecord(bitcoin, "60000", LocalDateTime.now())));
        when(priceRecordRepository.findByAssetAndRecordedAtAfter(eq(bitcoin), any()))
                .thenReturn(List.of(priceRecord(bitcoin, "50000", LocalDateTime.now().minusHours(24))));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(ethereum))
                .thenReturn(Optional.of(priceRecord(ethereum, "2850", LocalDateTime.now())));
        when(priceRecordRepository.findByAssetAndRecordedAtAfter(eq(ethereum), any()))
                .thenReturn(List.of());

        WalletPerformanceDto result = performanceService.calculatePerformance(1L, 24);

        assertThat(result.all()).hasSize(1);
        assertThat(result.all().get(0).symbol()).isEqualTo("bitcoin");
        assertThat(result.best().symbol()).isEqualTo("bitcoin");
        assertThat(result.worst().symbol()).isEqualTo("bitcoin");
    }

    @Test
    void shouldReturnNullBestWorstWhenNoDataAvailable() {
        Wallet wallet = wallet(1L);
        Asset bitcoin = asset(1L, "bitcoin", "Bitcoin");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletAssetRepository.findByWallet(wallet))
                .thenReturn(List.of(walletAsset(wallet, bitcoin, "1.0")));
        when(priceRecordRepository.findTopByAssetOrderByRecordedAtDesc(bitcoin))
                .thenReturn(Optional.empty());

        WalletPerformanceDto result = performanceService.calculatePerformance(1L, 24);

        assertThat(result.all()).isEmpty();
        assertThat(result.best()).isNull();
        assertThat(result.worst()).isNull();
    }

    @Test
    void shouldThrowNotFoundWhenWalletMissing() {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.calculatePerformance(99L, 24))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
