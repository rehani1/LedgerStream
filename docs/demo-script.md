# Demo Script

## Goal

Show the full paper-trading path in 60 to 90 seconds using either the public deployment or the local Docker Compose stack.

## Public Deployment

Public links:

- Frontend: <https://ledger-stream.vercel.app/>
- Backend: <https://ledgerstream-backend-5rk9.onrender.com/>
- Backend health: <https://ledgerstream-backend-5rk9.onrender.com/actuator/health>

Before running a browser demo, verify the deployed frontend and backend are wired together:

```bash
curl -i https://ledgerstream-backend-5rk9.onrender.com/actuator/health
curl -i -X OPTIONS https://ledgerstream-backend-5rk9.onrender.com/api/auth/register \
  -H 'Origin: https://ledger-stream.vercel.app' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,authorization,idempotency-key'
```

Expected backend health is `UP`. Expected CORS behavior is an allow-origin response for `https://ledger-stream.vercel.app`.

Provider prerequisites:

- Vercel must build with `VITE_API_BASE_URL=https://ledgerstream-backend-5rk9.onrender.com`.
- Render must set `BACKEND_CORS_ALLOWED_ORIGINS=https://ledger-stream.vercel.app`.
- Render, Neon, Upstash, Redpanda, and Vercel secrets must remain in provider dashboards.
- Redpanda Cloud topics must exist for `market.tick`, `order.created`, `order.filled`, `portfolio.updated`, `risk.updated`, and `audit.event`.
- A market-data producer must publish ticks to Redpanda Cloud before quote and order-fill demos.

As of the June 28, 2026 smoke test, the Render backend health endpoint was `UP`; direct backend registration, login, symbols, portfolio summary, positions, and ledger reads worked. Latest quote and risk endpoints returned `404` until market data and risk snapshots are produced. The public Vercel build still needed the final API base URL and Render CORS alignment before browser flows could be verified end to end.

## Browser Flow

1. Open the deployed or local frontend.
2. Log in with a demo account.
3. Start deterministic market replay or verify it is already running.
4. Watch live quote updates.
5. Submit a paper market or limit order.
6. Show the order status and fill.
7. Show portfolio cash and position updates.
8. Show append-only ledger entries.
9. Show risk metrics.
10. Show Prometheus or Grafana observability.

## Screenshot Placeholders

Capture real screenshots after the public frontend API base URL, backend CORS, and hosted market-data replay path are fully verified. Until then, use this table as the placeholder checklist.

| Screenshot | Placeholder path | Capture criteria |
| --- | --- | --- |
| Dashboard | `docs/assets/demo/dashboard.png` | Authenticated dashboard showing symbols, quote cards, stream state, and chart. |
| Order ticket | `docs/assets/demo/order-ticket.png` | Order form with symbol, side, type, quantity, limit price when selected, and idempotent submit state. |
| Portfolio | `docs/assets/demo/portfolio.png` | Portfolio summary with cash, total equity, positions, and P&L fields. |
| Ledger | `docs/assets/demo/ledger.png` | Append-only ledger table showing cash and quantity deltas from a fill. |
| Risk dashboard | `docs/assets/demo/risk-dashboard.png` | Latest risk snapshot and historical risk chart. |
| Grafana metrics | `docs/assets/observability/grafana-ledgerstream-overview.png` | `LedgerStream Overview` dashboard with API, order, tick, cache, JVM, and process panels. |
| GitHub Actions passing | `docs/assets/demo/github-actions-ci.png` | Latest `main` branch CI run passing on GitHub Actions. |

Do not commit screenshots that include access tokens, refresh tokens, provider secrets, private email addresses, or raw production credentials.

## 60-90 Second Demo Video Script

Target length: 75 seconds.

| Time | Screen | Narration |
| --- | --- | --- |
| 0-8s | README and live links | "LedgerStream is a deployed paper-trading platform with a Spring Boot event-driven backend, React dashboard, PostgreSQL, Redis, and Redpanda." |
| 8-15s | Login/register | "The demo starts with authenticated access. The backend uses JWT access tokens and refresh-token rotation." |
| 15-25s | Dashboard quotes | "Market data enters through deterministic replay, is published as `market.tick`, cached in Redis, persisted in PostgreSQL, and streamed to the dashboard." |
| 25-37s | Order ticket | "Orders require an `Idempotency-Key`, so duplicate submissions return the existing order instead of creating duplicate fills." |
| 37-48s | Order history/fill | "The backend publishes `order.created`; the execution consumer uses the latest quote and writes the fill transactionally." |
| 48-58s | Portfolio and ledger | "Cash, positions, fills, and append-only ledger entries are updated together inside the database transaction." |
| 58-66s | Risk dashboard | "Risk snapshots track total equity, cash, gross exposure, concentration, and unrealized P&L." |
| 66-75s | Grafana and CI | "The system exposes Prometheus metrics, structured logs, Grafana dashboards, CI, dependency scanning, and measured k6 baseline results." |

If the public market-data producer is not running yet, record the video against the seeded local Docker Compose stack and state that the hosted deployment uses the same service boundaries.

## Public API Smoke Flow

Use this when validating the deployed backend before browser testing. Do not print or store returned tokens.

1. Register a temporary demo-only user with `POST /api/auth/register`.
2. Call `GET /api/me`.
3. Call `GET /api/symbols`.
4. Call `GET /api/portfolio`.
5. Call `GET /api/portfolio/positions`.
6. Call `GET /api/portfolio/ledger`.
7. Call `GET /api/symbols/AAPL/quote`.
8. Submit a small market or limit order with a unique `Idempotency-Key`.
9. Call `GET /api/orders`.
10. Logout with `POST /api/auth/logout`.

If quote data has not been replayed into Redpanda/PostgreSQL/Redis yet, `GET /api/symbols/AAPL/quote` can return `404`. If the hosted Kafka credentials are missing or not mapped to the backend's `BACKEND_KAFKA_*` environment variables, order submission can stall or fail because the backend cannot publish `order.created`.

## Demo Readiness Checklist

- Public backend health returns `UP`.
- Vercel is rebuilt with `VITE_API_BASE_URL=https://ledgerstream-backend-5rk9.onrender.com`.
- Render CORS allows `https://ledger-stream.vercel.app`.
- Demo credentials are either self-registered for the session or seeded only in a controlled demo environment.
- A market-data producer publishes deterministic ticks to Redpanda Cloud, or the demo is run locally with the Compose worker profile.
- Screenshot placeholders above are replaced with real screenshots that do not expose secrets.
- The 60-90 second video is recorded and linked from README.

## Local Demo Data

Set `DEMO_SEED_ENABLED=true` in a local `.env` file to create the configured demo account and initial cash balance. Set `DEMO_ADMIN_SEED_ENABLED=true` with separate admin credentials when the demo needs the admin replay controls. Supported symbols are available after Flyway migrations run.

Deterministic market-data fixtures are available at `workers/market-data/data/sample_ticks.csv`. The worker can validate the fixture locally with:

```bash
cd workers/market-data
PYTHONPATH=src python -m ledgerstream_market_data replay --file data/sample_ticks.csv --dry-run
```

Admin users can open the Admin route and use Start Replay or Stop Replay to set backend replay state. The current MVP records that control action and shows queue-topic health; it does not launch the Python process. To publish ticks to Redpanda locally, run:

```bash
docker compose --profile worker up --build market-data-worker
```

After logging in locally, open the dashboard to see supported symbols, latest quote rows, stream connection state, and the selected symbol price chart. The frontend reads historical quote data from `/api/symbols/{ticker}/history` and consumes the authenticated SSE quote stream with the current access token.

Open the orders route, select a symbol, choose buy or sell, choose market or limit, submit a paper order, and watch the order history show pending, filled, rejected, or cancelled status. Then open the portfolio route to confirm cash, total equity, realized and unrealized P&L, open positions, and paginated ledger entries update from the backend portfolio APIs. Open the risk route to show total equity, gross exposure, largest-position concentration, unrealized P&L, and the historical snapshot chart.
