package com.cryptotrackr.infrastructure;

import com.cryptotrackr.dto.response.CoinCapAssetResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class CoinCapGateway {

    private final WebClient webClient;
    private final Retry retrySpec;

    @Autowired
    public CoinCapGateway(WebClient webClient, @Value("${coincap.max-retries:3}") int maxRetries) {
        this(webClient, maxRetries, Duration.ofSeconds(1));
    }

    CoinCapGateway(WebClient webClient, int maxRetries, Duration initialBackoff) {
        this.webClient = webClient;
        this.retrySpec = Retry.backoff(maxRetries, initialBackoff)
                .filter(this::isRetryable)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    public Optional<BigDecimal> fetchPrice(String assetId) {
        try {
            CoinCapAssetResponse response = webClient.get()
                    .uri("/assets/{id}", assetId)
                    .retrieve()
                    .bodyToMono(CoinCapAssetResponse.class)
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(retrySpec)
                    .block();

            return Optional.ofNullable(response)
                    .map(CoinCapAssetResponse::data)
                    .map(data -> new BigDecimal(data.priceUsd()));
        } catch (Exception e) {
            log.warn("Failed to fetch price for asset '{}': {}", assetId, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException e) {
            return e.getStatusCode().is5xxServerError();
        }
        return true;
    }
}
