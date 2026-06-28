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

## TODO

- Add k6 scripts.
- Run local stack load tests.
- Record raw command outputs or summaries.
- Add interpretation and limitations.
