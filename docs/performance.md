# Performance

## Measurement Policy

This document contains measured results only. Results are local baselines, not production capacity claims.

## Local Baseline Results

Measured on June 28, 2026 against a local Docker Compose stack.

| Metric | Result | Test Source |
| --- | ---: | --- |
| Order creation p95 latency | 82.52 ms | `load-tests/k6/order-create.js` |
| Quote API p95 latency | 101.86 ms | `load-tests/k6/quote-api.js` |
| Order creation throughput | 0.96 requests/sec | `load-tests/k6/order-create.js` |
| Quote API throughput | 4.84 requests/sec | `load-tests/k6/quote-api.js` |
| Market ticks consumed | 25 ticks, 0 failed | worker replay plus backend Prometheus metrics |
| Market tick replay rate | 10.9 ticks/sec | 25 ticks / 2.30 seconds wall-clock |
| API error rate under k6 load | 0.00% | k6 custom failure rates and `http_req_failed` |

The market tick replay rate uses wall-clock time for the one-off worker container command, including container startup and shutdown. It is useful as a local demo baseline, but it is not a pure backend consumer throughput benchmark.

## Test Environment

- Host: macOS 14.7.6 (23H626), Apple M2, arm64, 8 GB RAM.
- Docker: client/server 29.5.2.
- Docker Compose: v5.1.4.
- k6: v2.0.0, Go 1.26.3, darwin/arm64.
- Backend tests: Java 22.0.1 locally; backend container runs the Java 21 image from `backend/Dockerfile`.
- Compose project: `ledgerstream_perf`.
- Services: backend and frontend built locally, PostgreSQL `postgres:16-alpine`, Redis `redis:7-alpine`, Redpanda `redpandadata/redpanda:v24.3.5`, Prometheus `prom/prometheus:v2.55.1`, Grafana `grafana/grafana:11.3.0`.
- Host ports during the run: backend `18080`, frontend `15173`, PostgreSQL `15432`, Redis `16379`, Redpanda broker `19093`, Redpanda admin `19644`, Prometheus `19090`, Grafana `13000`.
- Test user: locally registered paper-trading user. k6 received an `AUTH_TOKEN`; no access tokens are committed in result files.
- Market data: `workers/market-data/data/sample_ticks.csv`, 25 deterministic ticks across `AAPL`, `MSFT`, `NVDA`, `TSLA`, and `SPY`.

## Commands Run

The local stack was started with alternate host ports because another local PostgreSQL container was already bound to `5432`.

```bash
COMPOSE_PROJECT_NAME=ledgerstream_perf \
POSTGRES_PORT=15432 \
REDIS_PORT=16379 \
REDPANDA_EXTERNAL_PORT=19093 \
REDPANDA_ADMIN_PORT=19644 \
BACKEND_PORT=18080 \
FRONTEND_PORT=15173 \
PROMETHEUS_PORT=19090 \
GRAFANA_PORT=13000 \
docker compose up --build -d
```

Backend verification:

```bash
cd backend
./mvnw test
```

Result: 125 tests passed, 0 failures, 0 errors, 0 skipped.

Worker replay:

```bash
COMPOSE_PROJECT_NAME=ledgerstream_perf \
POSTGRES_PORT=15432 \
REDIS_PORT=16379 \
REDPANDA_EXTERNAL_PORT=19093 \
REDPANDA_ADMIN_PORT=19644 \
BACKEND_PORT=18080 \
FRONTEND_PORT=15173 \
PROMETHEUS_PORT=19090 \
GRAFANA_PORT=13000 \
docker compose --profile worker run --rm --no-deps \
  -e MARKET_DATA_REPLAY_SPEED=240 \
  market-data-worker \
  python -m ledgerstream_market_data --log-level INFO replay \
    --file data/sample_ticks.csv \
    --speed 240
```

Result: `Market replay completed`; wall-clock `real 2.30`; backend Prometheus delta `ledgerstream_market_ticks_consumed_total 25.0`, `ledgerstream_market_ticks_failed_total 0.0`.

k6 order creation:

```bash
BASE_URL=http://localhost:18080 \
AUTH_TOKEN=<local access token> \
k6 run --summary-export load-tests/k6/results/order-create-summary.json \
  load-tests/k6/order-create.js
```

Profile: 1 VU, 30 seconds, 1-second sleep, `AAPL` market BUY, quantity `1`, unique `Idempotency-Key` per iteration.

Result: 29 requests, 0.96 requests/sec, average 44.03 ms, median 32.74 ms, p95 82.52 ms, max 209.52 ms, 0.00% failures, 58 checks passed and 0 failed.

k6 quote API:

```bash
BASE_URL=http://localhost:18080 \
AUTH_TOKEN=<local access token> \
k6 run --summary-export load-tests/k6/results/quote-api-summary.json \
  load-tests/k6/quote-api.js
```

Profile: 5 VUs, 30 seconds, 1-second sleep, symbols `AAPL,MSFT,NVDA,TSLA,SPY`.

Result: 150 requests, 4.84 requests/sec, average 32.13 ms, median 21.38 ms, p95 101.86 ms, max 112.83 ms, 0.00% failures, 300 checks passed and 0 failed.

## Interpretation

- These are low-load local baseline tests. They prove the stack can process authenticated paper-order creation and quote reads without errors in this environment.
- The order profile is intentionally limited to stay below the default per-user order creation rate limit. It should not be described as maximum order throughput.
- Quote throughput is shaped by the 5 VU, 1-second-sleep profile. It is not a saturation test.
- SSE quote-stream load is not measured yet because the current k6 scripts do not include an EventSource client.
- The committed k6 summaries were checked for token-like strings before commit.

## Backend Correctness Coverage

Fast backend unit tests cover deterministic financial calculations and safety checks before load measurements are collected:

- market BUY cash settlement, quantity updates, and weighted average cost
- market SELL cash settlement, quantity updates, average-cost reset, and realized P&L
- insufficient cash and insufficient share rejection paths
- idempotent order submission behavior
- risk gross exposure, unrealized P&L, and largest-position concentration

These tests do not produce throughput or latency claims; they are correctness guards for the later integration and load-test phases.

## Integration Test Strategy

Backend integration tests use Testcontainers with PostgreSQL and Redis to exercise the real Flyway schema, repositories, Redis quote cache, authentication registration, order creation, market fill execution, ledger writes, portfolio valuation, risk snapshots, duplicate idempotency handling, and user data isolation.

The integration test class is marked with Testcontainers' Docker-disabled skip behavior so local and CI runs without a Docker daemon do not fail the whole suite. In that case, Maven reports the integration tests as skipped. Redpanda/Kafka publishing is mocked in this integration layer because the current backend flow can be verified deterministically by invoking the execution service directly after order creation; a broker-backed event-flow test remains a later expansion.

## Worker Test Strategy

Market-data worker tests validate deterministic CSV parsing, invalid row handling, event serialization, dry-run replay output, publisher calls, replay timing calculations with an injected sleeper, and fixture-based buy-and-hold and moving-average crossover backtest metrics. These tests do not produce throughput or latency claims; replay throughput remains a planned measurement once the full local stack can be run.

## Frontend Test Strategy

Frontend tests use Vitest, React Testing Library, mocked API responses, and mocked quote stream callbacks to verify login form validation, quote dashboard rendering, stream connection states, order ticket validation, portfolio table rendering, risk summary rendering, and admin replay controls. The CI-friendly unit/component command is `npm run test:ci` from `frontend/`.

Playwright E2E tests run the login, quote dashboard, paper order, order history, portfolio, and ledger browser flow. The default `npm run e2e` path uses mocked backend responses for deterministic CI execution. Setting `E2E_MOCK_API=false` runs the same browser flow against a seeded local backend stack, but that mode requires Docker Compose services, demo credentials, and market data to be available.

## Load Test Scripts

k6 scripts are available under `load-tests/k6/`:

- `order-create.js` submits authenticated market order creation requests with a unique `Idempotency-Key` per iteration.
- `quote-api.js` reads authenticated latest quotes for a configurable ticker list.
- `lib/auth.js` accepts `AUTH_TOKEN` or logs in with `LOAD_TEST_EMAIL` and `LOAD_TEST_PASSWORD` lazily per VU.

Both scripts define target thresholds for p95 latency, request rate, and failure rate. The default order creation profile uses one VU and a one-second sleep to stay below the backend's default per-user order rate limit.

No k6 SSE streaming script is included yet because k6 does not provide a native EventSource client. Streaming load should be measured later with an EventSource-capable k6 extension or another tool that can count delivered events accurately.

Raw k6 summary JSON from the June 28, 2026 run is stored under `load-tests/k6/results/`.
