# Observability

## Goals

LedgerStream should be inspectable through health checks, structured logs, Prometheus metrics, and Grafana dashboards.

## Planned Metrics

- API request rate, latency, and error count.
- Market ticks consumed and failed.
- Orders created, filled, rejected, and cancelled.
- Active quote stream clients.
- Quote cache hits and misses.
- Portfolio and risk calculation latency.
- JVM runtime metrics.

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
