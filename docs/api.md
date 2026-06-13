# API

## Status

The API surface below is the target contract. Endpoints will be marked as implemented as backend work lands.

## Planned Endpoints

| Area | Method | Path | Auth | Notes |
| --- | --- | --- | --- | --- |
| Smoke | `GET` | `/api/ping` | Public | Implemented. Returns backend status and server timestamp. |
| Auth | `POST` | `/api/auth/register` | Public | Implemented. Create a user, zero-cash portfolio, audit event, access token, and refresh token. |
| Auth | `POST` | `/api/auth/login` | Public | Implemented. Return access and refresh tokens for valid credentials. |
| Auth | `POST` | `/api/auth/refresh` | Public | Implemented. Rotate refresh token and issue a new token pair. |
| Auth | `POST` | `/api/auth/logout` | Public | Implemented. Revoke the provided refresh token. |
| Auth | `GET` | `/api/me` | User | Implemented. Return current JWT principal. |
| Symbols | `GET` | `/api/symbols` | User | Implemented. List active supported symbols. |
| Symbols | `GET` | `/api/symbols/{ticker}` | User | Implemented. Return symbol metadata. |
| Quotes | `GET` | `/api/symbols/{ticker}/quote` | User | Implemented. Return latest quote from Redis with database fallback. |
| Quotes | `GET` | `/api/symbols/{ticker}/history?range=1d&limit=500` | User | Implemented. Return historical ticks with bounded result size. |
| Streaming | `GET` | `/api/stream/quotes?symbols=AAPL,MSFT` | User | Implemented. SSE quote stream. |
| Orders | `POST` | `/api/orders` | User | Requires `Idempotency-Key`. |
| Orders | `GET` | `/api/orders` | User | User-scoped order history. |
| Orders | `GET` | `/api/orders/{id}` | User | User-scoped order detail. |
| Orders | `POST` | `/api/orders/{id}/cancel` | User | Cancel pending orders. |
| Portfolio | `GET` | `/api/portfolio` | User | Summary with cash, equity, and P&L. |
| Portfolio | `GET` | `/api/portfolio/positions` | User | Position list. |
| Portfolio | `GET` | `/api/portfolio/ledger` | User | Paginated ledger entries. |
| Risk | `GET` | `/api/portfolio/risk` | User | Latest risk snapshot. |
| Risk | `GET` | `/api/portfolio/risk/history` | User | Historical risk snapshots. |
| Admin | `POST` | `/api/admin/market/replay/start` | Admin | Start deterministic replay control. |
| Admin | `POST` | `/api/admin/market/replay/stop` | Admin | Stop deterministic replay control. |
| Admin | `GET` | `/api/admin/queue-health` | Admin | Implemented. Returns current queue-health integration status. |
| Observability | `GET` | `/actuator/health` | Public or internal | Health checks. |
| Observability | `GET` | `/actuator/prometheus` | Internal | Prometheus metrics. |

## Error Shape

Standard error response:

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/orders",
  "requestId": "request-id"
}
```

Clients may send `X-Request-ID` with a safe ASCII value up to 128 characters. The backend echoes it in the `X-Request-ID` response header and includes it in standard API errors. If the header is absent or invalid, the backend generates a UUID request ID.

## Authentication

Implemented auth endpoints return this shape:

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresAt": "2026-01-01T00:15:00Z",
  "refreshToken": "opaque-refresh-token",
  "refreshTokenExpiresAt": "2026-01-08T00:00:00Z",
  "user": {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": "demo@example.com",
    "role": "USER"
  }
}
```

Send authenticated requests with:

```http
Authorization: Bearer <accessToken>
```

Login failures return a generic `401` message and do not reveal whether an email exists.

Refresh and logout accept the same body shape:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Refresh tokens are opaque values returned only at issue time. The backend stores only a SHA-256 hash, rotates the token on every successful refresh, rejects expired tokens, and treats reuse of an already-revoked refresh token as a suspicious event that revokes remaining active refresh tokens for that user.

All non-auth API endpoints require a bearer access token unless explicitly marked public. `/api/admin/**` endpoints require a user with the `ADMIN` role.

## Symbols And Quotes

Ticker path variables are normalized to uppercase and must be 1 to 16 characters using letters, digits, or dots. Unknown symbols return `404`.

`GET /api/symbols` returns active symbols:

```json
[
  {
    "id": "00000000-0000-0000-0000-000000000001",
    "ticker": "AAPL",
    "name": "Apple Inc.",
    "exchange": "NASDAQ",
    "assetType": "EQUITY",
    "currency": "USD",
    "active": true
  }
]
```

`GET /api/symbols/AAPL/quote` checks Redis first and falls back to the latest persisted PostgreSQL tick:

```json
{
  "symbol": "AAPL",
  "timestamp": "2026-01-02T14:34:00Z",
  "bid": 187.360000,
  "ask": 187.480000,
  "last": 187.420000,
  "volume": 136200,
  "source": "fixture"
}
```

`GET /api/symbols/AAPL/history?range=1d&limit=500` supports `range` values `5m`, `15m`, `1h`, `6h`, `1d`, and `5d`. `limit` defaults to `500` and must be between `1` and `500`.

```json
{
  "symbol": "AAPL",
  "range": "1d",
  "limit": 500,
  "ticks": [
    {
      "symbol": "AAPL",
      "timestamp": "2026-01-02T14:34:00Z",
      "bid": 187.360000,
      "ask": 187.480000,
      "last": 187.420000,
      "volume": 136200,
      "source": "fixture"
    }
  ]
}
```

## Real-Time Quote Stream

`GET /api/stream/quotes?symbols=AAPL,MSFT` returns `text/event-stream`. The `symbols` parameter is required, comma-separated, deduplicated, and capped at 25 symbols per stream. Each symbol must already exist in the symbol catalog.

The stream starts with a `ready` event:

```text
event: ready
data: {"symbols":["AAPL","MSFT"],"connectedAt":"2026-01-02T14:35:00Z"}
```

Quote events use the same quote payload as the latest quote endpoint:

```text
id: AAPL:2026-01-02T14:34:00Z
event: quote
data: {"symbol":"AAPL","timestamp":"2026-01-02T14:34:00Z","bid":187.360000,"ask":187.480000,"last":187.420000,"volume":136200,"source":"fixture"}
```

The backend sends available latest quotes immediately after subscription and broadcasts new `market.tick` updates after they are accepted by the ingestion path.

`GET /api/admin/queue-health` currently returns the configured event-topic contract. It does not claim live broker connectivity yet:

```json
{
  "status": "topics_configured",
  "checkedAt": "2026-01-01T00:00:00Z",
  "topics": {
    "marketTick": "market.tick",
    "orderCreated": "order.created",
    "orderFilled": "order.filled",
    "portfolioUpdated": "portfolio.updated",
    "riskUpdated": "risk.updated",
    "auditEvent": "audit.event"
  }
}
```

## Event Topics

LedgerStream uses JSON payloads on Kafka-compatible topics.

### `market.tick`

```json
{
  "eventId": "00000000-0000-0000-0000-000000000001",
  "symbol": "AAPL",
  "timestamp": "2026-01-01T14:30:00Z",
  "bid": 187.12,
  "ask": 187.18,
  "last": 187.15,
  "volume": 1000,
  "source": "fixture"
}
```

### `order.created`

```json
{
  "eventId": "00000000-0000-0000-0000-000000000002",
  "orderId": "00000000-0000-0000-0000-000000000003",
  "userId": "00000000-0000-0000-0000-000000000004",
  "symbol": "AAPL",
  "side": "BUY",
  "orderType": "MARKET",
  "quantity": 10.000000,
  "limitPrice": null,
  "createdAt": "2026-01-01T14:30:01Z"
}
```

### `order.filled`

```json
{
  "eventId": "00000000-0000-0000-0000-000000000005",
  "orderId": "00000000-0000-0000-0000-000000000003",
  "fillId": "00000000-0000-0000-0000-000000000006",
  "userId": "00000000-0000-0000-0000-000000000004",
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 10.000000,
  "price": 187.150000,
  "fee": 0.00,
  "filledAt": "2026-01-01T14:30:02Z"
}
```

### `portfolio.updated`

```json
{
  "eventId": "00000000-0000-0000-0000-000000000007",
  "userId": "00000000-0000-0000-0000-000000000004",
  "portfolioId": "00000000-0000-0000-0000-000000000008",
  "totalEquity": 100000.00,
  "cash": 98128.50,
  "updatedAt": "2026-01-01T14:30:03Z"
}
```

### `risk.updated`

```json
{
  "eventId": "00000000-0000-0000-0000-000000000009",
  "userId": "00000000-0000-0000-0000-000000000004",
  "totalEquity": 100000.00,
  "grossExposure": 1871.50,
  "largestPositionPct": 0.0187,
  "unrealizedPnl": 0.00,
  "createdAt": "2026-01-01T14:30:04Z"
}
```

### `audit.event`

```json
{
  "eventId": "00000000-0000-0000-0000-000000000010",
  "userId": "00000000-0000-0000-0000-000000000004",
  "action": "ORDER_CREATED",
  "requestId": "request-id",
  "metadata": {
    "symbol": "AAPL"
  },
  "createdAt": "2026-01-01T14:30:05Z"
}
```

## TODO

- Add concrete request and response examples after endpoints are implemented.
- Add idempotency semantics.
- Add pagination parameters and defaults.
- Add curl examples.
