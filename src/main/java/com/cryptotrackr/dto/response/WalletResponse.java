package com.cryptotrackr.dto.response;

import java.time.LocalDateTime;

public record WalletResponse(
        Long id,
        String name,
        LocalDateTime createdAt
) {}
