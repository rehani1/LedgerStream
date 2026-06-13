# Architecture

## Overview

LedgerStream is planned as an event-driven paper-trading system. Market data enters through a Python replay worker, moves through a Kafka-compatible stream, and is consumed by the Spring Boot backend for persistence, cache updates, order execution, portfolio accounting, risk snapshots, and client streaming.

## Target Services

| Service | Responsibility |
| --- | --- |
| `frontend` | Authenticated dashboard, quote stream display, order ticket, portfolio, ledger, and risk views. |
| `backend` | REST API, authentication, authorization, streaming gateway, order processing, ledger writes, risk calculations, metrics, and logs. |
| `market-data-worker` | Deterministic CSV replay and normalized `market.tick` event publishing. |
| `postgres` | Durable relational state for accounts, symbols, orders, fills, positions, ledger entries, risk snapshots, and audit events. |
| `redis` | Latest quote cache, hot-path reads, and future rate-limiting state. |
| `redpanda` | Kafka-compatible event backbone. |
| `prometheus` | Metrics scraping. |
| `grafana` | Dashboard visualization. |

## Planned Event Flow

```text
CSV replay
  -> market-data-worker
  -> market.tick
  -> backend consumer
  -> price_ticks, latest quote cache, quote stream, order evaluation
  -> fills, positions, ledger_entries, portfolio.updated, risk.updated
```

## Design Priorities

- Transactional correctness for cash, fills, positions, and ledger entries.
- Idempotent order creation through a user-scoped idempotency key.
- User-scoped authorization for all financial data.
- Deterministic replay data for demos and tests.
- Measured performance claims only after load tests are run.

## Backend Foundation

The backend starts as a Spring Boot 3 application with Web, Security, Validation, JPA, Flyway, Kafka, Actuator, Prometheus, PostgreSQL, and Testcontainers dependencies. The first exposed endpoints are `/api/ping` and `/actuator/health`; domain endpoints will be added behind authentication in later increments.

## TODO

- Add service diagram.
- Add order lifecycle diagram.
- Add failure handling and retry strategy.
- Add deployment topology.
- Add measured throughput and latency once load tests exist.
