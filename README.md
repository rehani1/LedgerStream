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

The backend echoes or generates `X-Request-ID` for request tracing and uses the same ID in standard API error responses.

Latest quote cache entries are stored in Redis under keys like `latest_quote:AAPL`; the cached JSON payload includes quote timestamp metadata so callers can detect stale prices before falling back to PostgreSQL.

Implemented auth endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/me`

### Demo Data

Supported symbols are seeded by Flyway: `AAPL`, `MSFT`, `NVDA`, `TSLA`, and `SPY`.

Demo account seeding is disabled by default. For local development only, set `DEMO_SEED_ENABLED=true` and provide `DEMO_USER_EMAIL`, `DEMO_USER_PASSWORD`, and `DEMO_USER_INITIAL_CASH`.

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
| Frontend | `5173` | Planned Vite dev server port. |

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
