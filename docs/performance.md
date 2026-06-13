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

## TODO

- Add k6 scripts.
- Run local stack load tests.
- Record raw command outputs or summaries.
- Add interpretation and limitations.
