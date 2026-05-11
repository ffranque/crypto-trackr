package com.cryptotrackr.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddAssetRequest(
        @NotBlank String symbol,
        @NotBlank String name,
        @NotBlank String code,
        @NotNull @DecimalMin("0.00000001") BigDecimal quantity,
        @NotNull @DecimalMin("0.00") BigDecimal purchasePrice,
        @NotNull LocalDate purchaseDate
) {}
