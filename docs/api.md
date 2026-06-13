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
| Symbols | `GET` | `/api/symbols` | User | List supported symbols. |
| Symbols | `GET` | `/api/symbols/{ticker}` | User | Return symbol metadata. |
| Quotes | `GET` | `/api/symbols/{ticker}/quote` | User | Return latest quote from Redis with database fallback. |
| Quotes | `GET` | `/api/symbols/{ticker}/history?range=1d` | User | Return historical ticks with bounded result size. |
| Streaming | `GET` | `/api/stream/quotes?symbols=AAPL,MSFT` | User | SSE quote stream. |
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
| Admin | `GET` | `/api/admin/queue-health` | Admin | Queue and consumer health. |
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

## TODO

- Add concrete request and response examples after endpoints are implemented.
- Add idempotency semantics.
- Add pagination parameters and defaults.
- Add curl examples.
