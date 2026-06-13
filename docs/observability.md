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

## Planned Logs

Backend logs should be structured JSON where practical and include request ID, method, path, status, latency, and safe contextual identifiers such as user ID, order ID, and symbol.

## TODO

- Add Prometheus scrape configuration.
- Add Grafana datasource provisioning.
- Add dashboard JSON.
- Add sample log lines.
- Add troubleshooting playbooks for failed orders, stream disconnects, and event ingestion failures.
