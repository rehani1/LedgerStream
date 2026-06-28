# Observability

## Goals

LedgerStream should be inspectable through health checks, structured logs, Prometheus metrics, and Grafana dashboards.

## Implemented Metrics

The backend exposes Prometheus metrics at `/actuator/prometheus`. The local Prometheus service scrapes `backend:8080/actuator/prometheus` through `infra/prometheus/prometheus.yml`.

Spring Boot and Micrometer provide JVM, process, HTTP server, datasource, and executor metrics. LedgerStream adds custom operational and business metrics:

- `ledgerstream_market_ticks_consumed_total`: accepted `market.tick` events applied to PostgreSQL and Redis. Duplicate historical rows are skipped, but the latest quote cache is still refreshed and the event is counted as consumed.
- `ledgerstream_market_ticks_failed_total`: malformed, unknown-symbol, or infrastructure-failed `market.tick` events.
- `ledgerstream_orders_created_total`: newly accepted paper orders.
- `ledgerstream_orders_filled_total`: paper orders filled by the execution engine.
- `ledgerstream_orders_rejected_total`: paper orders rejected by the execution engine.
- `ledgerstream_quote_stream_clients`: active SSE quote stream clients on the current backend instance.
- `ledgerstream_quote_stream_events_total`: quote SSE events sent by the backend.
- `ledgerstream_quote_stream_send_failures_total`: quote SSE send failures that caused the backend to close a stream.
- `ledgerstream_quote_cache_hits_total`: latest quote reads served from Redis.
- `ledgerstream_quote_cache_misses_total`: latest quote reads that fell back to PostgreSQL because Redis missed or was unavailable.
- `ledgerstream_portfolio_calculation_latency`: timer for portfolio summary and position valuation calculations. Prometheus exports timer series such as `_seconds_count`, `_seconds_sum`, and `_seconds_max`.

Planned custom metrics:

- Orders cancelled.
- Risk calculation latency.
- API-level business error counts by endpoint and reason.

## Health Checks

Spring Boot Actuator exposes `/actuator/health`. With Redis auto-configuration enabled, the backend reports Redis connectivity as part of health in local and deployed profiles. The test profile disables Redis health checks so unit and MVC tests do not require a running Redis server.

The Compose Redis service also has a container health check based on `redis-cli ping`.

## Backend Logs

Backend logs use Spring Boot structured JSON logging in the local profile through `logging.structured.format.console=logstash`. Request IDs are stored in MDC as `requestId` and echoed in the `X-Request-ID` header.

The structured logging format can be changed with `BACKEND_LOG_FORMAT`. Supported Spring Boot structured formats include `logstash`, `ecs`, and `gelf`.

Each completed HTTP request writes one request log entry with safe routing and timing fields:

- `requestId`
- `httpMethod`
- `httpPath`
- `endpoint`
- `httpStatus`
- `latencyMs`

Order lifecycle logs add scoped domain context where it is safe:

- `userId`
- `orderId`
- `symbol`
- `eventType`

Market tick rejection logs include `eventType=market.tick` and the submitted `symbol` when one is available.

Request logs intentionally do not include request bodies, query strings, authorization headers, cookies, passwords, access tokens, refresh tokens, API keys, or raw client IP addresses.

Example request log:

```json
{
  "@timestamp": "2026-06-28T16:21:33.102Z",
  "level": "INFO",
  "logger_name": "com.ledgerstream.web.RequestLoggingFilter",
  "message": "http_request completed",
  "requestId": "request-123",
  "httpMethod": "POST",
  "httpPath": "/api/orders",
  "endpoint": "/api/orders",
  "httpStatus": "201",
  "latencyMs": "42"
}
```

Example order lifecycle log:

```json
{
  "@timestamp": "2026-06-28T16:21:33.128Z",
  "level": "INFO",
  "logger_name": "com.ledgerstream.orders.OrderExecutionService",
  "message": "order_filled",
  "requestId": "request-123",
  "userId": "00000000-0000-0000-0000-000000000101",
  "orderId": "00000000-0000-0000-0000-000000000301",
  "symbol": "AAPL",
  "eventType": "order.filled"
}
```

## TODO

- Add Prometheus scrape configuration.
- Add Grafana datasource provisioning.
- Add dashboard JSON.
- Add troubleshooting playbooks for failed orders, stream disconnects, and event ingestion failures.
