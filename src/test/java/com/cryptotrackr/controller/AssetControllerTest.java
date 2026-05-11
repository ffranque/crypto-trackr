package com.cryptotrackr.controller;

import com.cryptotrackr.service.AssetService;
import com.cryptotrackr.domain.PriceRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssetController.class)
class AssetControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AssetService assetService;

    @Test
    void shouldReturnPriceHistoryWithMappedRecords() throws Exception {
        List<PriceRecord> records = List.of(
                PriceRecord.builder().id(1L).priceUsd(new BigDecimal("60000")).recordedAt(LocalDateTime.now()).build(),
                PriceRecord.builder().id(2L).priceUsd(new BigDecimal("61000")).recordedAt(LocalDateTime.now()).build(),
                PriceRecord.builder().id(3L).priceUsd(new BigDecimal("59000")).recordedAt(LocalDateTime.now()).build()
        );

        when(assetService.getPriceHistory(eq("bitcoin"), eq(24), any(Pageable.class)))
                .thenReturn(new PageImpl<>(records));

        mockMvc.perform(get("/assets/bitcoin/history").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].priceUsd").value(60000))
                .andExpect(jsonPath("$.content[0].asset").doesNotExist());
    }

    @Test
    void shouldRespectPageSizeParamInPriceHistory() throws Exception {
        List<PriceRecord> page = List.of(
                PriceRecord.builder().id(1L).priceUsd(new BigDecimal("60000")).recordedAt(LocalDateTime.now()).build()
        );

        when(assetService.getPriceHistory(eq("bitcoin"), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(page));

        mockMvc.perform(get("/assets/bitcoin/history").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void shouldReturn404WhenAssetNotFound() throws Exception {
        when(assetService.getPriceHistory(eq("unknown"), anyInt(), any(Pageable.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found: unknown"));

        mockMvc.perform(get("/assets/unknown/history"))
                .andExpect(status().isNotFound());
    }
}
