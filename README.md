# LedgerStream

Real-Time Paper Trading & Risk Platform

LedgerStream is an event-driven paper-trading platform that will ingest deterministic or live market data, stream quotes to authenticated clients, process paper orders, maintain an append-only portfolio ledger, calculate risk and P&L, and expose production-style observability and deployment artifacts.

## Target Architecture

The finished system is planned as a small monorepo with these services:

- `frontend`: React, TypeScript, and Vite dashboard.
- `backend`: Java 21 and Spring Boot 3 API, authentication, streaming gateway, and portfolio engine.
- `market-data-worker`: Python worker for deterministic CSV market replay and normalized tick publishing.
- `postgres`: relational store for users, symbols, ticks, orders, fills, positions, ledger entries, risk snapshots, and audit events.
- `redis`: latest quote cache, hot-path state, and future rate limiting.
- `redpanda`: Kafka-compatible event stream for market and portfolio events.
- `prometheus` and `grafana`: metrics collection and dashboards.

Planned event flow:

```text
CSV replay or market data source
  -> Python market-data worker
  -> Redpanda market.tick topic
  -> Spring Boot backend consumers
  -> PostgreSQL, Redis, SSE quote streams, fills, ledger, risk snapshots
  -> React dashboard
```

## Local Development

The target local command is:

```bash
docker compose up --build
```

This starts the local backend, frontend, PostgreSQL, Redis, Redpanda, Prometheus, and Grafana services. The market-data worker is available through the `worker` Compose profile.

### Backend

The backend is a Java 21 Spring Boot 3 service with a Maven wrapper.

```bash
cd backend
./mvnw test
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The initial public smoke endpoints are:

- `GET /api/ping`
- `GET /actuator/health`

The backend echoes or generates `X-Request-ID` for request tracing and uses the same ID in standard API error responses. In the local profile, backend console logs use structured JSON and include request completion fields plus safe order/event context such as `userId`, `orderId`, `symbol`, and `eventType`.

Latest quote cache entries are stored in Redis under keys like `latest_quote:AAPL`; the cached JSON payload includes quote timestamp metadata so callers can detect stale prices before falling back to PostgreSQL.

Backend event publishing is configured for Redpanda/Kafka-compatible topics including `market.tick`, `order.created`, `order.filled`, `portfolio.updated`, `risk.updated`, and `audit.event`.

The backend consumes `market.tick` events, persists historical ticks to PostgreSQL, and refreshes Redis latest quote cache entries. Duplicate ticks with the same symbol, timestamp, and source are skipped for historical storage but still update the hot cache.

Market order execution consumes `order.created`, creates fills, settles portfolio cash and positions, and appends immutable ledger entries in one transaction. BUY fills record negative cash and positive quantity deltas; SELL fills record positive cash and negative quantity deltas.

Operational metrics are exposed at `GET /actuator/prometheus`. Custom metrics cover market tick ingestion, order creation/fill/rejection counts, active quote stream clients, quote cache hits and misses, and portfolio valuation latency.

Grafana is provisioned with the `LedgerStream Overview` dashboard and a default Prometheus datasource. Start it with:

```bash
docker compose up -d prometheus grafana backend
```

Open `http://localhost:3000` and use the Compose Grafana credentials: `admin` / `ledgerstream-local`.

Backend unit tests cover the core financial invariants: fill settlement, average cost, realized P&L, rejection paths, idempotency, and risk concentration.

Backend integration tests use Testcontainers for PostgreSQL and Redis. They verify registration, quote seeding, idempotent order creation, market fill settlement, positions, ledger entries, portfolio/risk views, and user data isolation. If Docker is not running, those tests are skipped by Testcontainers instead of failing the suite.

Implemented auth endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/me`

Implemented symbol and quote endpoints:

- `GET /api/symbols`
- `GET /api/symbols/{ticker}`
- `GET /api/symbols/{ticker}/quote`
- `GET /api/symbols/{ticker}/history?range=1d&limit=500`
- `GET /api/stream/quotes?symbols=AAPL,MSFT`

Implemented order endpoints:

- `POST /api/orders`
- `GET /api/orders`
- `GET /api/orders/{id}`
- `POST /api/orders/{id}/cancel`

Implemented portfolio endpoints:

- `GET /api/portfolio`
- `GET /api/portfolio/positions`
- `GET /api/portfolio/ledger?page=0&size=10`

Implemented risk endpoints:

- `GET /api/portfolio/risk`
- `GET /api/portfolio/risk/history?page=0&size=50`

Implemented admin endpoints:

- `GET /api/admin/market/replay/status`
- `POST /api/admin/market/replay/start`
- `POST /api/admin/market/replay/stop`
- `GET /api/admin/queue-health`

Replay controls are authenticated admin-only backend state controls for the local demo. They record audit events and expose the intended replay state, while the Python worker is still started through the Compose worker profile.

### Market Data Worker

The market-data worker validates deterministic CSV replay fixtures and can publish normalized `market.tick` events to Redpanda.

```bash
cd workers/market-data
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
PYTHONPATH=src pytest
PYTHONPATH=src python -m ledgerstream_market_data replay --file data/sample_ticks.csv --dry-run
```

The included sample fixture has 25 deterministic ticks across `AAPL`, `MSFT`, `NVDA`, `TSLA`, and `SPY`. Worker tests cover CSV parsing, invalid row handling, event serialization, dry-run output, publish calls, and replay timing without real sleeps.

The Compose service is behind the `worker` profile and publishes to Redpanda when enabled:

```bash
docker compose --profile worker up --build market-data-worker
```

### Frontend

The frontend is a React, TypeScript, and Vite app under `frontend/`.

```bash
cd frontend
npm install
npm run build
npm test -- --run
npm run test:ci
npm run e2e
npm run dev
```

Set `VITE_API_BASE_URL` for local development or `FRONTEND_API_BASE_URL` when building through Docker Compose. The app shell includes routes for dashboard, login/register, portfolio, orders, risk, and admin replay controls. The Admin nav item is shown only for authenticated users with the `ADMIN` role.

Frontend tests cover login form validation, quote dashboard rendering and stream states, order ticket validation and idempotency headers, portfolio summary and ledger tables, risk summary/history rendering, and admin replay controls.

Frontend authentication is wired to the backend register, login, refresh, logout, and `/api/me` endpoints. Tokens are stored in browser `sessionStorage` for the MVP; see [Security](docs/security.md) for the tradeoff.

The dashboard fetches supported symbols and latest quotes, opens the authenticated quote stream, and charts the selected symbol's intraday history with Recharts.

The portfolio route renders cash, total equity, realized and unrealized P&L, open positions, and paginated append-only ledger entries from the backend portfolio APIs.

The orders route includes a market order ticket with per-submission idempotency keys, double-submit protection, order status feedback, cancellation for pending orders, and a user-scoped order history table.

The risk route renders latest total equity, cash, gross exposure, concentration, unrealized P&L, and a historical risk chart from the backend risk snapshot APIs.

### End-to-End Tests

Playwright E2E tests live under `frontend/e2e/`. The default `npm run e2e` path starts the Vite dev server and uses mocked backend responses, which makes the browser flow CI-friendly without Docker.

Install the Chromium browser once per machine or CI image:

```bash
cd frontend
npm run e2e:install
```

To run the same browser flow against a seeded local stack, start Compose with demo data and disable API mocks:

```bash
DEMO_SEED_ENABLED=true DEMO_USER_PASSWORD=Password123! docker compose --profile worker up --build -d
cd frontend
E2E_MOCK_API=false E2E_DEMO_EMAIL=demo@example.com E2E_DEMO_PASSWORD=Password123! npx playwright test
cd ..
docker compose down
```

### Load Tests

k6 scripts live under `load-tests/k6/` for order creation and quote API load testing. They are parameterized with `BASE_URL`, `AUTH_TOKEN`, or `K6_EMAIL` and `K6_PASSWORD`.

```bash
k6 run load-tests/k6/order-create.js
k6 run load-tests/k6/quote-api.js
```

Install k6 before running these scripts. The performance docs keep p95 latency, request rate, and failure rate as TODO until tests are run against a documented environment.

### Demo Data

Supported symbols are seeded by Flyway: `AAPL`, `MSFT`, `NVDA`, `TSLA`, and `SPY`.

Demo account seeding is disabled by default. For local development only, set `DEMO_SEED_ENABLED=true` and provide `DEMO_USER_EMAIL`, `DEMO_USER_PASSWORD`, and `DEMO_USER_INITIAL_CASH`. To seed a local admin for replay controls, also set `DEMO_ADMIN_SEED_ENABLED=true` with `DEMO_ADMIN_EMAIL` and `DEMO_ADMIN_PASSWORD`.

### Local Service Ports

| Service | Port | Notes |
| --- | --- | --- |
| PostgreSQL | `5432` | Database for core platform state. |
| Redis | `6379` | Hot quote cache and future rate-limiting state. |
| Redpanda broker | `19092` | Kafka-compatible external listener for local tools. |
| Redpanda admin | `9644` | Admin and health interface. |
| Prometheus | `9090` | Metrics UI and scrape storage. |
| Grafana | `3000` | Dashboard UI; local default user is `admin`. |
| Backend | `8080` | Planned Spring Boot API port. |
| Frontend | `5173` | Vite dev server locally, or Compose-served static dashboard. |

## Documentation

- [Architecture](docs/architecture.md)
- [API](docs/api.md)
- [Data Model](docs/data-model.md)
- [Deployment](docs/deployment.md)
- [Security](docs/security.md)
- [Observability](docs/observability.md)
- [Performance](docs/performance.md)
- [Demo Script](docs/demo-script.md)

## Measurement Policy

Latency, throughput, cache-hit rate, coverage, and other performance claims must be measured before they are documented. Until tests are implemented and run, performance documentation uses explicit placeholders rather than invented numbers.
