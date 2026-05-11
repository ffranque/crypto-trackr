package com.cryptotrackr.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateWalletRequest(
        @NotBlank String name,
        @NotNull Long userId
) {}
