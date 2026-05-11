package com.cryptotrackr.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CoinCapGatewayTest {

    private static final String BITCOIN_BODY =
            "{\"data\":{\"id\":\"bitcoin\",\"symbol\":\"BTC\",\"name\":\"Bitcoin\",\"priceUsd\":\"67000.00\"}}";

    private CoinCapGateway gateway(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test-host")
                .exchangeFunction(exchangeFunction)
                .build();
        return new CoinCapGateway(webClient, 3, Duration.ofMillis(10));
    }

    private static ClientResponse ok() {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(BITCOIN_BODY)
                .build();
    }

    private static ClientResponse status(HttpStatus status) {
        return ClientResponse.create(status).build();
    }

    @Test
    void shouldReturnPriceOnSuccess() {
        CoinCapGateway gw = gateway(request -> Mono.just(ok()));

        Optional<BigDecimal> result = gw.fetchPrice("bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("67000.00");
    }

    @Test
    void shouldRetryOnServerError() {
        AtomicInteger calls = new AtomicInteger();
        CoinCapGateway gw = gateway(request -> {
            if (calls.incrementAndGet() <= 2) {
                return Mono.just(status(HttpStatus.INTERNAL_SERVER_ERROR));
            }
            return Mono.just(ok());
        });

        Optional<BigDecimal> result = gw.fetchPrice("bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("67000.00");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyWhenRetriesExhausted() {
        AtomicInteger calls = new AtomicInteger();
        CoinCapGateway gw = gateway(request -> {
            calls.incrementAndGet();
            return Mono.just(status(HttpStatus.INTERNAL_SERVER_ERROR));
        });

        Optional<BigDecimal> result = gw.fetchPrice("bitcoin");

        assertThat(result).isEmpty();
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void shouldNotRetryOnTooManyRequests() {
        AtomicInteger calls = new AtomicInteger();
        CoinCapGateway gw = gateway(request -> {
            calls.incrementAndGet();
            return Mono.just(status(HttpStatus.TOO_MANY_REQUESTS));
        });

        Optional<BigDecimal> result = gw.fetchPrice("bitcoin");

        assertThat(result).isEmpty();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void shouldNotRetryOn4xxErrors() {
        AtomicInteger calls = new AtomicInteger();
        CoinCapGateway gw = gateway(request -> {
            calls.incrementAndGet();
            return Mono.just(status(HttpStatus.NOT_FOUND));
        });

        Optional<BigDecimal> result = gw.fetchPrice("bitcoin");

        assertThat(result).isEmpty();
        assertThat(calls.get()).isEqualTo(1);
    }
}
