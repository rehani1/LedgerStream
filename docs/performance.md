# Performance

## Measurement Policy

This document will contain measured results only. Do not fill in latency, throughput, error rate, cache-hit, or coverage numbers until the relevant tests have been run.

## Planned Measurements

| Metric | Result | Test Source |
| --- | --- | --- |
| Order creation p95 latency | TODO: measure | k6 order test |
| Quote API p95 latency | TODO: measure | k6 quote test |
| Order creation throughput | TODO: measure | k6 order test |
| Market tick ingestion rate | TODO: measure | replay plus backend metrics |
| API error rate under load | TODO: measure | k6 summary |

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

## TODO

- Add k6 scripts.
- Run local stack load tests.
- Record raw command outputs or summaries.
- Add interpretation and limitations.
