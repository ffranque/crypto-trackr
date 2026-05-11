package com.cryptotrackr.controller;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.domain.Wallet;
import com.cryptotrackr.domain.WalletAsset;
import com.cryptotrackr.service.PerformanceService;
import com.cryptotrackr.service.WalletService;
import com.cryptotrackr.dto.AssetPerformanceDto;
import com.cryptotrackr.dto.WalletPerformanceDto;
import com.cryptotrackr.dto.WalletValueDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean WalletService walletService;
    @MockitoBean PerformanceService performanceService;

    private static final String ADD_ASSET_BODY =
            "{\"symbol\":\"bitcoin\",\"name\":\"Bitcoin\",\"code\":\"BTC\",\"quantity\":\"1.0\"," +
            "\"purchasePrice\":\"45000.00\",\"purchaseDate\":\"2024-01-15\"}";

    @Test
    void shouldReturn201WhenWalletCreatedSuccessfully() throws Exception {
        when(walletService.createWallet("My Wallet", 1L))
                .thenReturn(Wallet.builder().id(1L).name("My Wallet").build());

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Wallet\",\"userId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("My Wallet"))
                .andExpect(jsonPath("$.walletAssets").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void shouldReturn400WhenWalletNameIsBlank() throws Exception {
        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"userId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenUserNotFoundOnCreateWallet() throws Exception {
        when(walletService.createWallet(anyString(), eq(99L)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: 99"));

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Wallet\",\"userId\":99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn201WithFlatFieldsWhenAssetAdded() throws Exception {
        Asset asset  = Asset.builder().id(1L).symbol("bitcoin").name("Bitcoin").code("BTC").build();
        Wallet wallet = Wallet.builder().id(1L).name("My Wallet").build();
        WalletAsset wa = WalletAsset.builder().id(1L).wallet(wallet).asset(asset)
                .quantity(new BigDecimal("1.0"))
                .purchasePrice(new BigDecimal("45000.00"))
                .purchaseDate(LocalDate.of(2024, 1, 15))
                .build();

        when(walletService.addAsset(eq(1L), eq("bitcoin"), eq("Bitcoin"), eq("BTC"),
                any(), any(), any())).thenReturn(wa);

        mockMvc.perform(post("/wallets/1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADD_ASSET_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.symbol").value("bitcoin"))
                .andExpect(jsonPath("$.name").value("Bitcoin"))
                .andExpect(jsonPath("$.quantity").value(1.0))
                .andExpect(jsonPath("$.wallet").doesNotExist())
                .andExpect(jsonPath("$.asset").doesNotExist());
    }

    @Test
    void shouldReturn404WhenWalletNotFoundOnAddAsset() throws Exception {
        when(walletService.addAsset(eq(99L), anyString(), anyString(), anyString(),
                any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found: 99"));

        mockMvc.perform(post("/wallets/99/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADD_ASSET_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnWalletValueDto() throws Exception {
        WalletValueDto dto = new WalletValueDto(1L, "My Wallet", new BigDecimal("150000.00"), List.of());
        when(walletService.calculateWalletValue(1L)).thenReturn(dto);

        mockMvc.perform(get("/wallets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValueUsd").value(150000.00));
    }

    @Test
    void shouldReturnHistoricalWalletValueForGivenDate() throws Exception {
        WalletValueDto dto = new WalletValueDto(1L, "My Wallet", new BigDecimal("100000.00"), List.of());
        when(walletService.calculateWalletValueAt(eq(1L), eq(LocalDate.of(2024, 1, 15)))).thenReturn(dto);

        mockMvc.perform(get("/wallets/1/value").param("at", "2024-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValueUsd").value(100000.00));
    }

    @Test
    void shouldReturnPerformanceDtoWithBestWorstAndAll() throws Exception {
        AssetPerformanceDto btc = new AssetPerformanceDto("bitcoin", "Bitcoin", "BTC",
                new BigDecimal("1.0"), new BigDecimal("60000"), new BigDecimal("50000"),
                new BigDecimal("20.00"));
        AssetPerformanceDto eth = new AssetPerformanceDto("ethereum", "Ethereum", "ETH",
                new BigDecimal("5.0"), new BigDecimal("2850"), new BigDecimal("3000"),
                new BigDecimal("-5.00"));
        WalletPerformanceDto dto = new WalletPerformanceDto(btc, eth, List.of(btc, eth));
        when(performanceService.calculatePerformance(1L, 24)).thenReturn(dto);

        mockMvc.perform(get("/wallets/1/performance").param("windowHours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.best.symbol").value("bitcoin"))
                .andExpect(jsonPath("$.worst.symbol").value("ethereum"))
                .andExpect(jsonPath("$.all.length()").value(2));
    }
}
