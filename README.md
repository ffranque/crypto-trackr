# CryptoTrackr

A Spring Boot REST API for tracking cryptocurrency wallet values and performance. Users can manage wallets and asset positions, and the system automatically fetches live prices from the CoinCap API every 30 seconds.

---

## Prerequisites

| Tool | Version |
|---|---|
| Java | 25 |
| Maven | 3.9+ |
| PostgreSQL | 16+ |
---

## Setup

### 1. Create the database

```bash
psql -U postgres -c "CREATE DATABASE cryptotrackr;"
```

### 2. Configure the application

Edit `src/main/resources/application.yaml` and verify the following values match your local environment:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cryptotrackr
    username: postgres
    password: postgres

coincap:
  base-url: https://rest.coincap.io/v3
  fetch-interval-ms: 30000
  api-key: <your-coincap-api-key>
```

A CoinCap API key is required. Register for free at [coincap.io](https://coincap.io).

### 3. Schema

The schema is managed by Hibernate (`ddl-auto: update`). Tables are created automatically on first startup — no migration scripts needed.

---

## Running the project

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

Price fetching begins automatically. Wait approximately 30 seconds after startup for the first price records to be populated before querying wallet values or performance.

---

## Running the tests

```bash
./mvnw test
```
---

## API reference

### Users

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/users` | Create a user |
| `GET` | `/users/{id}` | Get a user by ID |

**Create user — request:**
```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username": "bivar", "email": "bivar@example.com"}'
```

**Response:**
```json
{
  "id": 1,
  "username": "bivar",
  "email": "bivar@example.com",
  "createdAt": "2024-06-01T10:00:00"
}
```

---

### Wallets

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/wallets` | Create a wallet |
| `POST` | `/wallets/{id}/assets` | Add an asset purchase lot to a wallet |
| `GET` | `/wallets/{id}` | Current wallet value (total + per asset) |
| `GET` | `/wallets/{id}/value?at=YYYY-MM-DD` | Historical wallet value at a given date |
| `GET` | `/wallets/{id}/performance?windowHours=24` | Asset performance over a time window |

**Create wallet — request:**
```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{"name": "My Portfolio", "userId": 1}'
```

**Response:**
```json
{
  "id": 1,
  "name": "My Portfolio",
  "createdAt": "2024-06-01T10:00:00"
}
```

---

**Add asset lot — request:**
```bash
curl -X POST http://localhost:8080/wallets/1/assets \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "bitcoin",
    "name": "Bitcoin",
    "code": "BTC",
    "quantity": "1.5",
    "purchasePrice": "45000.00",
    "purchaseDate": "2024-01-15"
  }'
```

All fields are required. `quantity` must be ≥ `0.00000001`; `purchasePrice` must be ≥ `0.00`.

**Response:**
```json
{
  "id": 1,
  "symbol": "bitcoin",
  "name": "Bitcoin",
  "quantity": 1.5,
  "purchasePrice": 45000.00,
  "purchaseDate": "2024-01-15"
}
```

---

**Current wallet value:**
```bash
curl http://localhost:8080/wallets/1
```

**Response:**
```json
{
  "walletId": 1,
  "walletName": "My Portfolio",
  "totalValueUsd": 105000.00,
  "assets": [
    {
      "symbol": "bitcoin",
      "name": "Bitcoin",
      "code": "BTC",
      "quantity": 1.5,
      "priceUsd": 67000.00,
      "totalValueUsd": 100500.00
    },
    {
      "symbol": "ethereum",
      "name": "Ethereum",
      "code": "ETH",
      "quantity": 2.0,
      "priceUsd": 2250.00,
      "totalValueUsd": 4500.00
    }
  ]
}
```

Assets with no price record yet are omitted from the response.

---

**Historical wallet value:**
```bash
curl "http://localhost:8080/wallets/1?at=2024-06-01"
```

---

**Performance over a time window:**
```bash
curl "http://localhost:8080/wallets/1/performance?windowHours=24"
```

`windowHours` defaults to `24`. Assets with no price history within the window are omitted.

**Response:**
```json
{
  "best": {
    "symbol": "bitcoin",
    "name": "Bitcoin",
    "code": "BTC",
    "quantity": 1.5,
    "priceUsdNow": 67000.00,
    "priceUsdThen": 64000.00,
    "changePercent": 4.69
  },
  "worst": {
    "symbol": "ethereum",
    "name": "Ethereum",
    "code": "ETH",
    "quantity": 2.0,
    "priceUsdNow": 2250.00,
    "priceUsdThen": 2400.00,
    "changePercent": -6.25
  },
  "all": [ ... ]
}
```

`best` and `worst` are `null` when no asset has price history in the window. `all` is the full list sorted by `changePercent` descending.

---

### Assets

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/assets/{symbol}/history?hours=24` | Price history for an asset (paginated) |

```bash
# Default: page 0, 20 records, sorted by recordedAt descending
curl "http://localhost:8080/assets/bitcoin/history?hours=24"

# Custom page size and page number
curl "http://localhost:8080/assets/bitcoin/history?hours=24&size=50&page=1"

# Custom sort
curl "http://localhost:8080/assets/bitcoin/history?hours=24&sort=recordedAt,asc"
```

**Response** — standard Spring `Page` envelope:
```json
{
  "content": [
    { "id": 1, "priceUsd": "67000.00", "recordedAt": "2024-06-01T10:30:00" }
  ],
  "totalElements": 120,
  "totalPages": 6,
  "number": 0,
  "size": 20
}
```

---

## Caching

`GET /wallets/{id}` resolves the current price for each asset in the wallet. Without caching, every request would hit the database once per asset — redundant work since prices only update every 30 seconds.

### How it works

`LatestPriceCache` is an in-memory `ConcurrentHashMap<symbol, price>` shared across the application:

- **Written** by `AssetPriceFetcher` immediately after each successful price save. The cache is never updated for empty gateway responses or fetch errors, so it only reflects prices that are actually persisted.
- **Read** by `WalletService.calculateWalletValue()` as the first lookup. If the symbol is not in the cache (cold start), the service falls back to the database query transparently.

---

## Resilience: retries and rate limiting

Both behaviours are configured in `application.yaml` under the `coincap` prefix and take effect automatically — no code changes needed to tune them.

```yaml
coincap:
  max-retries: 3
  max-concurrent-fetches: 5
```

### Retry logic

`CoinCapGateway` uses Reactor's `Retry.backoff` to transparently retry failed requests before surfacing the failure to the scheduler:

| Scenario | Behaviour |
|---|---|
| 5xx server error | Retried with exponential backoff (initial 1 s, doubles each attempt) |
| Network error / timeout | Retried (transient) |
| 429 Too Many Requests | **Not retried** — backing off under rate limit would worsen the situation |
| 4xx client error | **Not retried** — not a transient condition |
| All retries exhausted | Logs a warning, returns empty — the asset is skipped for this fetch cycle |

Each retry attempt has its own independent 5-second timeout. A single asset fetch can therefore take up to ~35 seconds in the worst case (5 s per attempt × 4 attempts + backoff delays) before giving up.

### Concurrent fetch limiting

`AssetPriceFetcher` uses a `Semaphore` to cap the number of simultaneous outbound calls per fetch cycle. Without this, all assets would be dispatched to the CoinCap API at once, which could trigger 429 responses when the wallet holds many assets.

With `max-concurrent-fetches: 5`, at most 5 gateway calls are in-flight at any moment. The remaining virtual threads block cheaply at `semaphore.acquire()` until a slot becomes free. A thread interrupted while waiting logs a warning and skips that asset for the current cycle.

---

## Assumptions

### Functional

- **Per-lot asset model** — each call to `POST /wallets/{id}/assets` creates a new purchase lot with its own quantity, purchase price, and purchase date. Multiple lots of the same asset are aggregated by symbol for valuation and performance responses.

- **Asset identity** — assets are identified by their CoinCap symbol (e.g. `bitcoin`, `ethereum`). An asset row is created automatically the first time it is added to any wallet. Subsequent wallets adding the same symbol reuse the existing asset.

- **Current value** — calculated using the most recent price record available. If no price has been fetched yet for an asset it is omitted from the response.

- **Historical value** — calculated using the last recorded price at or before 23:59:59 on the requested date. Only lots with a `purchaseDate` on or before the requested date are included.

- **Performance** — percentage change between the oldest and newest price records within the requested time window (`windowHours`, default 24). Assets with insufficient price history within the window are omitted.

- **Performance response shape** — returns `best` (highest % change), `worst` (lowest % change), and `all` (full list sorted best to worst). `best` and `worst` are `null` when no price history is available.

### Technical

- **Java 25 + Spring Boot 3.5.0** — Spring Boot 3.5.0 is the minimum required version because it ships ASM 9.8, which supports Java 25 class file version 69. Earlier versions fail with a `ClassFormatException`.

- **Lombok 1.18.38** — explicitly pinned because Lombok 1.18.36 and earlier fail on Java 25 with a `TypeTag::UNKNOWN` compiler error.

- **Virtual threads** — enabled globally via `spring.threads.virtual.enabled: true`. Price fetching uses a `VirtualThreadPerTaskExecutor` so each asset fetch runs concurrently without blocking OS threads.

- **Schema management** — Hibernate `ddl-auto: update` is used for development convenience. New columns are added automatically on startup; existing data is preserved.

- **`user_account` table name** — the `User` entity maps to the table `user_account` to avoid conflicting with the PostgreSQL reserved keyword `user`.

- **WebFlux for HTTP client only** — Spring WebFlux is included solely for `WebClient`. The application is a standard synchronous Spring MVC app; `.block()` is used intentionally in the gateway layer.

- **CoinCap v3 API** — the base URL and API key are externalised to `application.yaml`. The `Authorization: Bearer <key>` and `Accept: application/json` headers are applied globally via `WebClientConfig`.

- **In-process price cache** — `LatestPriceCache` is a `ConcurrentHashMap` updated by the price fetcher and read by wallet valuation. No external cache dependency.

- **Retry and rate limiting** — `CoinCapGateway` retries on 5xx and network errors using exponential backoff (up to `max-retries` attempts). 429 is never retried. `PriceFetcherService` enforces `max-concurrent-fetches` parallel outbound calls via a `Semaphore`. Both limits are externalised to `application.yaml`.
