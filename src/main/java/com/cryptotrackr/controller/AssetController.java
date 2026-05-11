package com.cryptotrackr.controller;

import com.cryptotrackr.dto.response.PriceRecordResponse;
import com.cryptotrackr.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping("/{symbol}/history")
    public Page<PriceRecordResponse> getPriceHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "24") int hours,
            @PageableDefault(size = 20, sort = "recordedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return assetService.getPriceHistory(symbol, hours, pageable)
                .map(r -> new PriceRecordResponse(r.getId(), r.getPriceUsd(), r.getRecordedAt()));
    }
}
