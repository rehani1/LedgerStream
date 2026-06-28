# Performance

## Measurement Policy

This document will contain measured results only. Do not fill in latency, throughput, error rate, cache-hit, or coverage numbers until the relevant tests have been run.

## Planned Measurements

| Metric | Result | Test Source |
| --- | --- | --- |
| Order creation p95 latency | TODO: measure | `load-tests/k6/order-create.js` |
| Quote API p95 latency | TODO: measure | `load-tests/k6/quote-api.js` |
| Order creation throughput | TODO: measure | `load-tests/k6/order-create.js` |
| Market tick ingestion rate | TODO: measure | replay plus backend metrics |
| API error rate under load | TODO: measure | k6 custom failure rates |

## Test Environment

TODO: document machine or CI environment, service versions, virtual users, duration, and data set after the tests are run.

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

Market-data worker tests validate deterministic CSV parsing, invalid row handling, event serialization, dry-run replay output, publisher calls, and replay timing calculations with an injected sleeper. These tests do not produce throughput or latency claims; replay throughput remains a planned measurement once the full local stack can be run.

## Frontend Test Strategy

Frontend tests use Vitest, React Testing Library, mocked API responses, and mocked quote stream callbacks to verify login form validation, quote dashboard rendering, stream connection states, order ticket validation, portfolio table rendering, risk summary rendering, and admin replay controls. The CI-friendly unit/component command is `npm run test:ci` from `frontend/`.

Playwright E2E tests run the login, quote dashboard, paper order, order history, portfolio, and ledger browser flow. The default `npm run e2e` path uses mocked backend responses for deterministic CI execution. Setting `E2E_MOCK_API=false` runs the same browser flow against a seeded local backend stack, but that mode requires Docker Compose services, demo credentials, and market data to be available.

## Load Test Scripts

k6 scripts are available under `load-tests/k6/`:

- `order-create.js` submits authenticated market order creation requests with a unique `Idempotency-Key` per iteration.
- `quote-api.js` reads authenticated latest quotes for a configurable ticker list.
- `lib/auth.js` accepts `AUTH_TOKEN` or logs in with `K6_EMAIL` and `K6_PASSWORD` during setup.

Both scripts define target thresholds for p95 latency, request rate, and failure rate. These are pass/fail goals, not measured results. The default order creation profile uses one VU and a one-second sleep to stay below the backend's default per-user order rate limit.

No k6 SSE streaming script is included yet because k6 does not provide a native EventSource client. Streaming load should be measured later with an EventSource-capable k6 extension or another tool that can count delivered events accurately.

## TODO

- Run local stack load tests.
- Record raw command outputs or summaries.
- Add interpretation and limitations.
