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

This repository is currently in the initial scaffold stage. Service-specific setup commands will be added as the backend, worker, frontend, and local infrastructure are implemented.

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
