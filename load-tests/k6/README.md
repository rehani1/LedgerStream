# k6 Load Tests

These scripts define repeatable load tests for LedgerStream API paths. They define target metrics, but they do not document measured results. Record actual results in `docs/performance.md` only after running the scripts against a known environment.

## Prerequisites

- Install k6 locally: `brew install k6`, `choco install k6`, or use the official package for your OS.
- Start a local backend with PostgreSQL, Redis, and Redpanda.
- Create or seed a load-test user.
- Seed latest quotes before running quote or order tests that depend on market prices.

## Authentication

Create a test user if one is not already seeded:

```bash
export BASE_URL=http://localhost:8080
curl -sS -X POST "$BASE_URL/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"loadtest@example.com","password":"Password123!"}'
```

Provide an existing token:

```bash
export BASE_URL=http://localhost:8080
export AUTH_TOKEN=<access-token>
```

Or let the scripts log in during `setup()`:

```bash
export BASE_URL=http://localhost:8080
export K6_EMAIL=loadtest@example.com
export K6_PASSWORD=Password123!
```

To create a token manually:

```bash
curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"loadtest@example.com","password":"Password123!"}'
```

Copy only the returned `accessToken` into `AUTH_TOKEN`. Do not commit tokens or command output containing tokens.

## Order Creation

```bash
k6 run load-tests/k6/order-create.js
```

Useful parameters:

```bash
K6_VUS=1 K6_DURATION=30s K6_SYMBOL=AAPL K6_ORDER_QUANTITY=1 k6 run load-tests/k6/order-create.js
```

The default order test uses one VU and a one-second sleep so it stays below the backend's default per-user order rate limit. Increase load only after adjusting rate limits or using multiple test users.

Target metrics:

- p95 order creation latency from `ledgerstream_order_create_duration`
- request rate from `http_reqs{endpoint:order-create}`
- failure rate from `ledgerstream_order_create_failures`

## Quote API

```bash
k6 run load-tests/k6/quote-api.js
```

Useful parameters:

```bash
K6_VUS=5 K6_DURATION=30s K6_SYMBOLS=AAPL,MSFT,NVDA,TSLA,SPY k6 run load-tests/k6/quote-api.js
```

Target metrics:

- p95 quote latency from `ledgerstream_quote_api_duration`
- request rate from `http_reqs{endpoint:quote-api}`
- failure rate from `ledgerstream_quote_api_failures`

## Streaming

No SSE streaming k6 script is included yet. k6 does not provide a native EventSource client, and approximating a long-lived SSE stream with plain HTTP would not measure client event handling honestly. Use a future xk6/EventSource-capable tool or Playwright-based stream instrumentation for streaming-specific load tests.
