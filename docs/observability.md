# Observability

## Goals

LedgerStream should be inspectable through health checks, structured logs, Prometheus metrics, and Grafana dashboards.

## Planned Metrics

- API request rate, latency, and error count.
- Orders created, filled, rejected, and cancelled.
- Active quote stream clients.
- Quote cache hits and misses.
- Portfolio and risk calculation latency.
- JVM runtime metrics.

## Implemented Metrics

The backend records market ingestion counters:

- `ledgerstream_market_ticks_consumed_total`: accepted `market.tick` events applied to PostgreSQL and Redis. Duplicate historical rows are skipped, but the latest quote cache is still refreshed and the event is counted as consumed.
- `ledgerstream_market_ticks_failed_total`: malformed, unknown-symbol, or infrastructure-failed `market.tick` events.
- `ledgerstream_quote_stream_clients`: active SSE quote stream clients on the current backend instance.
- `ledgerstream_quote_stream_events_total`: quote SSE events sent by the backend.
- `ledgerstream_quote_stream_send_failures_total`: quote SSE send failures that caused the backend to close a stream.

## Health Checks

Spring Boot Actuator exposes `/actuator/health`. With Redis auto-configuration enabled, the backend reports Redis connectivity as part of health in local and deployed profiles. The test profile disables Redis health checks so unit and MVC tests do not require a running Redis server.

The Compose Redis service also has a container health check based on `redis-cli ping`.

## Planned Logs

Backend logs use Spring Boot structured JSON logging in the local profile through `logging.structured.format.console=logstash`. Request IDs are stored in MDC as `requestId` and echoed in the `X-Request-ID` header. Future domain code should add safe contextual MDC fields such as `userId`, `orderId`, and `symbol` around the smallest useful scope and must not log secrets or tokens.

The structured logging format can be changed with `BACKEND_LOG_FORMAT`. Supported Spring Boot structured formats include `logstash`, `ecs`, and `gelf`.

## TODO

- Add Prometheus scrape configuration.
- Add Grafana datasource provisioning.
- Add dashboard JSON.
- Add sample log lines.
- Add troubleshooting playbooks for failed orders, stream disconnects, and event ingestion failures.
