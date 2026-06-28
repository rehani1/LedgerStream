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

## Frontend Foundation

The frontend is a Vite React TypeScript app. It uses React Router for dashboard, authentication, portfolio, orders, and risk routes; TanStack Query for API-backed state; and a small API client abstraction that reads `VITE_API_BASE_URL`. Docker builds the static bundle and serves it through Nginx with SPA route fallback.

## Redis Cache

The backend has a Redis-backed latest quote cache abstraction. Latest quote entries use the key pattern `latest_quote:{SYMBOL}` by default, with symbols normalized to uppercase. Values are JSON payloads that include the quote timestamp, bid, ask, last price, volume, and source. No TTL is applied yet because stale detection should use the embedded timestamp and future quote APIs can fall back to PostgreSQL.

Symbol and quote REST APIs are authenticated. Latest quote reads check Redis first and fall back to the newest persisted tick in PostgreSQL. Historical quote reads are bounded by a supported range and a maximum limit of 500 ticks.

The quote stream endpoint uses Server-Sent Events. Clients subscribe to up to 25 symbols per connection. The backend keeps an in-memory subscriber registry and broadcasts accepted `market.tick` updates after persistence and Redis cache refresh. This is correct for the single-backend MVP; multi-instance deployment will need shared pub/sub fanout or sticky routing.

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

A disabled-by-default market tick connectivity listener is available through `BACKEND_KAFKA_CONNECTIVITY_CONSUMER_ENABLED=true` for local broker wiring checks.

The backend now consumes `market.tick` events through the `marketTickKafkaListenerContainerFactory`. Each accepted tick resolves its symbol, writes a historical `price_ticks` row unless the same `(symbol, timestamp, source)` already exists, refreshes the Redis latest quote cache, broadcasts the quote to SSE clients, and records fresh risk snapshots for users with open positions in that symbol. Invalid payloads and unknown symbols are rejected and counted without retrying; unexpected infrastructure failures are allowed to propagate to Kafka retry/error handling. The consumer can be disabled with `BACKEND_MARKET_TICK_CONSUMER_ENABLED=false`.

The backend also consumes `order.created` events through the `orderCreatedKafkaListenerContainerFactory`. The execution service reloads the stored order by ID and only evaluates orders that are still `PENDING` and have type `MARKET`; limit orders remain pending for a later matching flow. Market buys use the latest ask price with a last-price fallback, while market sells use the latest bid price with a last-price fallback. Orders are rejected when no quote is available, no positive executable price exists, the buyer has insufficient cash, or the seller has insufficient shares.

When a market order is executable, the service creates a zero-fee fill, marks the order `FILLED`, settles portfolio cash and position state, appends a ledger entry, records a risk snapshot, publishes `risk.updated`, and publishes `order.filled` in the same transactional service boundary. BUY fills decrease cash, increase quantity, recalculate weighted average cost, and append `BUY_FILL` ledger rows. SELL fills increase cash, decrease quantity, update realized P&L, and append `SELL_FILL` ledger rows. Portfolio summary events remain a follow-on layer.

The order execution consumer can be disabled with `BACKEND_ORDER_CREATED_CONSUMER_ENABLED=false`, which is useful for API-only tests and local debugging without automatic fills.

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
