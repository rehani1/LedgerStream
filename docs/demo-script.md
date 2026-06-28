# Demo Script

## Goal

Show the full paper-trading path in 60 to 90 seconds once the MVP is implemented.

## Planned Flow

1. Open the deployed or local frontend.
2. Log in with a demo account.
3. Start deterministic market replay or verify it is already running.
4. Watch live quote updates.
5. Submit a paper market order.
6. Show the order status and fill.
7. Show portfolio cash and position updates.
8. Show append-only ledger entries.
9. Show risk metrics.
10. Show Prometheus or Grafana observability.

## TODO

- Add demo credentials only when safe and demo-only.
- Add screenshot links.
- Add video link or final narration.
- Add fallback local demo commands.

## Local Demo Data

Set `DEMO_SEED_ENABLED=true` in a local `.env` file to create the configured demo account and initial cash balance. Supported symbols are available after Flyway migrations run.

Deterministic market-data fixtures are available at `workers/market-data/data/sample_ticks.csv`. The worker can validate the fixture locally with:

```bash
cd workers/market-data
PYTHONPATH=src python -m ledgerstream_market_data replay --file data/sample_ticks.csv --dry-run
```

To publish those ticks to Redpanda locally, run:

```bash
docker compose --profile worker up --build market-data-worker
```

After logging in locally, open the dashboard to see supported symbols, latest quote rows, stream connection state, and the selected symbol price chart. The frontend reads historical quote data from `/api/symbols/{ticker}/history` and consumes the authenticated SSE quote stream with the current access token.

Open the orders route, select a symbol, choose buy or sell, submit a market paper order, and watch the order history show pending, filled, rejected, or cancelled status. Then open the portfolio route to confirm cash, total equity, realized and unrealized P&L, open positions, and paginated ledger entries update from the backend portfolio APIs. Open the risk route to show total equity, gross exposure, largest-position concentration, unrealized P&L, and the historical snapshot chart.
