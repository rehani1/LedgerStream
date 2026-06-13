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

The backend starts as a Spring Boot 3 application with Web, Security, Validation, JPA, Redis, Flyway, Kafka, Actuator, Prometheus, PostgreSQL, and Testcontainers dependencies. The first exposed endpoints are `/api/ping` and `/actuator/health`; domain endpoints are added behind authentication as their backing services land.

## Redis Cache

The backend has a Redis-backed latest quote cache abstraction. Latest quote entries use the key pattern `latest_quote:{SYMBOL}` by default, with symbols normalized to uppercase. Values are JSON payloads that include the quote timestamp, bid, ask, last price, volume, and source. No TTL is applied yet because stale detection should use the embedded timestamp and future quote APIs can fall back to PostgreSQL.

## Event Streaming

Backend event streaming is configured through Spring Kafka for Redpanda-compatible brokers. Producer JSON serialization is configured with idempotent producer settings and `acks=all`. Type headers are disabled so worker and frontend-adjacent tooling can consume plain JSON by topic contract.

Configured topics:

| Topic | Purpose |
| --- | --- |
| `market.tick` | Normalized quote ticks from replay or ingestion workers. |
| `order.created` | User order submission events. |
| `order.filled` | Simulated fill events. |
| `portfolio.updated` | Portfolio summary updates. |
| `risk.updated` | Risk snapshot updates. |
| `audit.event` | Security and financial audit events. |

A disabled-by-default market tick connectivity listener is available through `BACKEND_KAFKA_CONNECTIVITY_CONSUMER_ENABLED=true` for local broker wiring checks. Real tick persistence is implemented in the market ingestion unit.

## Market Data Worker

The Python worker is scaffolded under `workers/market-data` with a CLI entry point:

```bash
PYTHONPATH=src python -m ledgerstream_market_data replay --file data/sample_ticks.csv --dry-run
```

The worker validates replay configuration and deterministic CSV fixtures. `data/sample_ticks.csv` contains 25 ticks across `AAPL`, `MSFT`, `NVDA`, `TSLA`, and `SPY`, with bid, ask, last, volume, timestamp, and source fields. Each replayed row becomes a normalized `market.tick` JSON event with a deterministic UUIDv5 `eventId`. `--dry-run` prints events without Kafka; the Compose worker profile publishes to Redpanda.

## TODO

- Add service diagram.
- Add order lifecycle diagram.
- Add failure handling and retry strategy.
- Add deployment topology.
- Add measured throughput and latency once load tests exist.
