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
| Orders | `POST` | `/api/orders` | User | Implemented. Requires `Idempotency-Key`. |
| Orders | `GET` | `/api/orders` | User | Implemented. User-scoped order history. |
| Orders | `GET` | `/api/orders/{id}` | User | Implemented. User-scoped order detail. |
| Orders | `POST` | `/api/orders/{id}/cancel` | User | Implemented. Cancel pending orders. |
| Portfolio | `GET` | `/api/portfolio` | User | Implemented. Summary with cash, equity, and P&L. |
| Portfolio | `GET` | `/api/portfolio/positions` | User | Implemented. Position list with quote-derived valuations. |
| Portfolio | `GET` | `/api/portfolio/ledger?page=0&size=50` | User | Implemented. Paginated append-only ledger entries. |
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

The frontend auth flow posts credentials to the implemented auth endpoints, stores the returned token pair in `sessionStorage` for the current browser session, verifies stored access tokens with `GET /api/me`, and attempts refresh-token rotation when a stored access token is no longer accepted.

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

## Orders

The order API creates user-scoped paper orders with a required `Idempotency-Key` header, normalizes ticker input, validates positive quantities, requires positive limit prices for limit orders, stores market orders with `limitPrice: null`, and returns the existing order for duplicate `(user, idempotencyKey)` submissions without publishing a second event.

New orders start as `PENDING` and publish an `order.created` event. Pending orders can transition to `CANCELLED`; non-pending cancellation attempts return a conflict error.

Market order execution is asynchronous from the REST submission path. The backend consumes `order.created`, looks up the stored order, and executes only `MARKET` orders that are still `PENDING`. `LIMIT` orders intentionally remain pending until the limit-order matching flow is implemented.

Current market execution assumptions:

- BUY orders execute at the latest ask price, falling back to last price when ask is unavailable.
- SELL orders execute at the latest bid price, falling back to last price when bid is unavailable.
- Missing quotes or non-positive executable prices reject the order.
- BUY orders require enough portfolio cash for notional value plus the current zero-fee model.
- SELL orders require enough existing position quantity.
- Filled market orders create a fill, settle portfolio cash and position state in the same transaction, mark the order `FILLED`, and publish `order.filled`.
- Rejected market orders are marked `REJECTED` with a safe rejection reason and do not create fills.

Portfolio settlement uses these rounding assumptions: cash, fees, and realized P&L are rounded to 2 decimal places with `HALF_UP`; prices, quantities, and average cost are rounded to 6 decimal places with `HALF_UP`. BUY fills decrease cash by `price * quantity + fee`, increase quantity, and recalculate weighted average cost. SELL fills increase cash by `price * quantity - fee`, decrease quantity, and add realized P&L as `(execution price - average cost) * quantity - fee`. A full sell leaves a zero-quantity position row with average cost reset to zero.

Each filled market order also appends one ledger entry in the same transaction as the fill, cash update, and position update. BUY fill ledger rows record a negative cash delta and positive quantity delta. SELL fill ledger rows record a positive cash delta and negative quantity delta. The ledger row links the user, portfolio, order, fill, symbol, execution price, and metadata including side, order type, and fee.

Filled orders now create risk snapshots and publish `risk.updated`. Portfolio summary events remain a follow-on layer.

`POST /api/orders`

```http
Idempotency-Key: order-2026-01-02-0001
Content-Type: application/json
```

```json
{
  "symbol": "AAPL",
  "side": "BUY",
  "orderType": "MARKET",
  "quantity": 10.000000
}
```

First submissions return `201 Created`; duplicate idempotency submissions return `200 OK` with the original order:

```json
{
  "id": "00000000-0000-0000-0000-000000000010",
  "symbol": "AAPL",
  "side": "BUY",
  "orderType": "MARKET",
  "quantity": 10.000000,
  "limitPrice": null,
  "status": "PENDING",
  "rejectionReason": null,
  "createdAt": "2026-01-02T14:35:00Z",
  "updatedAt": "2026-01-02T14:35:00Z"
}
```

`GET /api/orders` returns only the authenticated user's order history. `GET /api/orders/{id}` returns `404` for missing or cross-user orders. `POST /api/orders/{id}/cancel` returns the updated order when cancellation succeeds and `409` when the order is no longer pending.

## Portfolio

Portfolio endpoints are authenticated and user-scoped. Missing portfolios return `404`; one user cannot request another user's positions or ledger because all reads are filtered by the authenticated user ID.

Position valuation uses the latest quote `last` price when available. If no latest quote exists, the API falls back to average cost for valuation, sets `lastPrice` to `null`, uses `valuationSource: "COST_BASIS_FALLBACK"`, and reports `unrealizedPnl: 0.00` for that position.

`GET /api/portfolio`

```json
{
  "portfolioId": "00000000-0000-0000-0000-000000000100",
  "baseCurrency": "USD",
  "cash": 98125.20,
  "marketValue": 1874.80,
  "totalEquity": 100000.00,
  "realizedPnl": 0.00,
  "unrealizedPnl": 0.00,
  "positionsCount": 1,
  "pricedPositionsCount": 1,
  "updatedAt": "2026-01-02T14:35:00Z"
}
```

`GET /api/portfolio/positions`

```json
[
  {
    "id": "00000000-0000-0000-0000-000000000101",
    "symbol": "AAPL",
    "quantity": 10.000000,
    "avgCost": 187.480000,
    "lastPrice": 187.480000,
    "valuationPrice": 187.480000,
    "valuationSource": "LATEST_QUOTE",
    "marketValue": 1874.80,
    "costBasis": 1874.80,
    "unrealizedPnl": 0.00,
    "realizedPnl": 0.00,
    "updatedAt": "2026-01-02T14:35:00Z"
  }
]
```

`GET /api/portfolio/ledger?page=0&size=50`

`page` is zero-based. `size` must be between `1` and `100`.

```json
{
  "entries": [
    {
      "id": "00000000-0000-0000-0000-000000000102",
      "entryType": "BUY_FILL",
      "cashDelta": -1874.80,
      "symbol": "AAPL",
      "quantityDelta": 10.000000,
      "price": 187.480000,
      "orderId": "00000000-0000-0000-0000-000000000103",
      "fillId": "00000000-0000-0000-0000-000000000104",
      "createdAt": "2026-01-02T14:35:00Z",
      "metadata": {
        "orderSide": "BUY",
        "orderType": "MARKET",
        "fee": 0.00
      }
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1
}
```

## Risk

Risk snapshots are calculated after successful fills and after accepted market ticks for users holding the ticked symbol. The backend persists snapshots in `risk_snapshots` and publishes `risk.updated`; REST endpoints for latest and historical risk reads are still the next API layer.

Formula summary:

- `totalEquity = cash + net position market value`
- `grossExposure = sum(abs(position market value))`
- `largestPositionPct = largest abs(position market value) / totalEquity * 100`
- `unrealizedPnl = sum((latest price - avgCost) * quantity)`

If a latest quote is unavailable, risk valuation falls back to average cost and uses zero unrealized P&L for that position. Cash, equity, exposure, and P&L use 2 decimal places with `HALF_UP`; concentration uses 4 decimal places.

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
  "largestPositionPct": 1.8715,
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
