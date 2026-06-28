# Deployment

## Selected Path

The production-like MVP path is:

| Layer | Provider | Notes |
| --- | --- | --- |
| Frontend | Vercel | Builds `frontend/` with Vite and serves the static dashboard. |
| Backend | Render Web Service | Builds `backend/Dockerfile` and runs the Spring Boot API. |
| PostgreSQL | Neon | Stores users, tokens, symbols, ticks, orders, fills, positions, ledger entries, risk snapshots, and audit events. |
| Redis | Upstash Redis | Stores latest quote cache entries and future distributed rate-limit state. |
| Event stream | Redpanda Cloud | Kafka-compatible event stream for market ticks, order events, portfolio updates, risk updates, and audit events. |

Kubernetes is intentionally out of scope for the MVP. The deployment should use managed services and platform secret managers.

## Live Deployment

Public URLs:

| Component | URL |
| --- | --- |
| Frontend | <https://ledger-stream.vercel.app/> |
| Backend | <https://ledgerstream-backend-5rk9.onrender.com/> |
| Backend health | <https://ledgerstream-backend-5rk9.onrender.com/actuator/health> |

The deployed environment uses:

- Vercel for the React/Vite frontend.
- Render Web Service for the Spring Boot backend on branch `main`.
- Neon Postgres for the application database. Flyway has applied the schema migrations successfully.
- Upstash Redis for the managed Redis cache. The initial placeholder-token configuration was corrected in provider environment variables.
- Redpanda Cloud in `us-east-1` for Kafka-compatible topics.

Redpanda Cloud topics:

- `market.tick`
- `order.created`
- `order.filled`
- `portfolio.updated`
- `risk.updated`
- `audit.event`

Redpanda Cloud non-secret connection details:

| Setting | Value |
| --- | --- |
| Kafka bootstrap server | `d90povjl9b9vq71tdo30.any.us-east-1.mpx.prd.cloud.redpanda.com:9092` |
| SASL mechanism | `SCRAM-SHA-256` |
| Application username | `ledgerstream-app` |

The Redpanda password, Neon credentials, Upstash credentials, JWT secret, and provider tokens are secrets and must stay in the Render, Vercel, Neon, Upstash, and Redpanda dashboards.

Verification on June 28, 2026:

```bash
curl -i https://ledgerstream-backend-5rk9.onrender.com/actuator/health
```

Expected result:

```json
{"status":"UP","groups":["liveness","readiness"]}
```

Current follow-up items before browser trading demos:

- Vercel must be rebuilt with `VITE_API_BASE_URL=https://ledgerstream-backend-5rk9.onrender.com`.
- Render must allow the production frontend origin with `BACKEND_CORS_ALLOWED_ORIGINS=https://ledger-stream.vercel.app`.
- The public quote/order fill path requires a market-data producer publishing `market.tick` events to the hosted Redpanda cluster. Local Redpanda remains available through Docker Compose for local demos and tests.

## Local Deployment

```bash
docker compose up --build
```

Compose starts the frontend, backend, PostgreSQL, Redis, Redpanda, Prometheus, and Grafana services. The market-data worker is behind the `worker` profile:

```bash
docker compose --profile worker up --build market-data-worker
```

The backend health check is:

```bash
curl -i http://localhost:8080/actuator/health
```

The frontend container exposes `/health` through Nginx.

## Frontend On Vercel

Vercel should be configured with:

| Setting | Value |
| --- | --- |
| Root directory | `frontend` |
| Install command | `npm ci` |
| Build command | `npm run build` |
| Output directory | `dist` |
| Required env var | `VITE_API_BASE_URL=https://ledgerstream-backend-5rk9.onrender.com` |

`frontend/vercel.json` defines the Vite build settings and rewrites all dashboard routes to `index.html` for client-side routing.

The live Vercel project uses `frontend` as the root directory, the Vite preset, `npm run build`, and `dist` output. After backend deployment, redeploy the frontend with `VITE_API_BASE_URL` set to the public backend URL. A stale Vercel build that still points at a placeholder backend URL will load the shell but cannot call LedgerStream APIs.

## Backend On Render

Create a Render Web Service from the repository:

| Setting | Value |
| --- | --- |
| Root directory | `backend` |
| Environment | Docker |
| Branch | `main` |
| Docker build context | `backend/.` |
| Dockerfile path | `backend/Dockerfile` when configured from the repo root, or `Dockerfile` when the Render root is `backend` |
| Health check path | `/actuator/health` |
| Default profile | `production` from the Docker image |

The backend image runs as a non-root user, sets JVM memory limits through `JAVA_OPTS`, exposes port `8080`, and includes a container health check against `/actuator/health`.

## Backend Environment Variables

Set production values in Render's environment variable manager:

| Variable | Required | Example |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes | `production` |
| `SERVER_PORT` | Usually platform-set | `8080` |
| `SPRING_DATASOURCE_URL` | Yes | `jdbc:postgresql://<neon-host>/<db>?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Yes | Neon database user |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Neon database password |
| `SPRING_DATA_REDIS_URL` | Yes for Upstash | `rediss://:<token>@<host>:6379` |
| `REDIS_HOST` / `REDIS_PORT` | Optional fallback | Non-TLS Redis host and port for local-style deployments |
| `BACKEND_CORS_ALLOWED_ORIGINS` | Yes | `https://<vercel-app>.vercel.app` |
| `BACKEND_JWT_SECRET` | Yes | At least 32 random bytes, stored only as a secret |
| `BACKEND_JWT_ISSUER` | Recommended | `ledgerstream-render` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Yes for Redpanda Cloud | `d90povjl9b9vq71tdo30.any.us-east-1.mpx.prd.cloud.redpanda.com:9092` |
| `BACKEND_KAFKA_CONSUMER_GROUP` | Recommended | `ledgerstream-backend` |
| `BACKEND_KAFKA_SECURITY_PROTOCOL` | Yes for Redpanda Cloud | `SASL_SSL` |
| `BACKEND_KAFKA_SASL_MECHANISM` | Yes for Redpanda Cloud | `SCRAM-SHA-256` |
| `BACKEND_KAFKA_SASL_USERNAME` | Yes for Redpanda Cloud | `ledgerstream-app` |
| `BACKEND_KAFKA_SASL_PASSWORD` | Yes for Redpanda Cloud | Broker service account secret |
| `BACKEND_KAFKA_SASL_JAAS_CONFIG` | Alternative to username/password | Full JAAS string, stored only as a secret |
| `BACKEND_MARKET_TICK_CONSUMER_ENABLED` | Optional | `true` with hosted Kafka, `false` for API-only demo |
| `BACKEND_ORDER_CREATED_CONSUMER_ENABLED` | Optional | `true` with hosted Kafka, `false` for API-only demo |
| `DEMO_SEED_ENABLED` | Demo only | `true` for controlled demo seeding |
| `DEMO_USER_EMAIL` | Demo only | `demo@example.com` |
| `DEMO_USER_PASSWORD` | Demo only | Stored as platform secret |
| `DEMO_USER_INITIAL_CASH` | Demo only | `100000.00` |

The `production` profile requires real datasource, CORS, and JWT secret values. Do not rely on the local defaults in production.

The backend defines custom Kafka producer and consumer factories that read `ledgerstream.kafka.*` properties. In Render, that means SASL/TLS values must be provided through the `BACKEND_KAFKA_*` environment variables above. `SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL`, `SPRING_KAFKA_PROPERTIES_SASL_MECHANISM`, and `SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG` are useful for Spring Boot auto-configured clients, but they do not configure LedgerStream's custom factories by themselves.

## Database Migrations

Flyway runs automatically during backend startup. On a new Neon database:

1. Create the database and user.
2. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
3. Deploy the backend.
4. Confirm `/actuator/health` returns `UP`.
5. Confirm the seeded symbol rows exist by calling `GET /api/symbols` with a valid access token.

For a manual migration check before cutting traffic, run the backend image against the production database from a one-off job using the same environment variables and `SPRING_PROFILES_ACTIVE=production`. Keep `spring.flyway.enabled=true`; the application validates the JPA schema after Flyway runs.

## Redis And Quote Cache

Use Upstash Redis with TLS and pass the connection URL through `SPRING_DATA_REDIS_URL`. Latest quote values are stored under `latest_quote:{SYMBOL}` by default. If Redis is unavailable, quote reads attempt PostgreSQL fallback, but tick ingestion and cache writes should be treated as unhealthy in production.

Do not enter placeholder values such as `<token>` in Render. Use the exact Upstash TLS URL or split host/port/password settings supported by Spring Data Redis.

## Event Stream And Replay

When Redpanda Cloud is available, set the Kafka bootstrap and SASL variables for both backend and the market-data worker:

```bash
SPRING_KAFKA_BOOTSTRAP_SERVERS=<redpanda-bootstrap>
BACKEND_KAFKA_SECURITY_PROTOCOL=SASL_SSL
BACKEND_KAFKA_SASL_MECHANISM=SCRAM-SHA-256
BACKEND_KAFKA_SASL_USERNAME=<service-account>
BACKEND_KAFKA_SASL_PASSWORD=<service-secret>
MARKET_DATA_KAFKA_BOOTSTRAP_SERVERS=<redpanda-bootstrap>
MARKET_DATA_KAFKA_SECURITY_PROTOCOL=SASL_SSL
MARKET_DATA_KAFKA_SASL_MECHANISM=SCRAM-SHA-256
MARKET_DATA_KAFKA_SASL_USERNAME=<service-account>
MARKET_DATA_KAFKA_SASL_PASSWORD=<service-secret>
```

For a simple public demo without a hosted worker, keep replay controls in `backend_state` mode and run the Python worker only during scripted demos. If no market-data producer is publishing ticks to Redpanda Cloud, authenticated quote reads can return `404` and market orders will not complete the automatic fill path. If no hosted Kafka broker is available, set `BACKEND_MARKET_TICK_CONSUMER_ENABLED=false` and `BACKEND_ORDER_CREATED_CONSUMER_ENABLED=false` and document that live tick replay and automatic fills are disabled for that demo environment.

## CORS

Set `BACKEND_CORS_ALLOWED_ORIGINS` to the exact Vercel origin:

```bash
BACKEND_CORS_ALLOWED_ORIGINS=https://ledger-stream.vercel.app
```

Do not use `*` with credentials enabled. Add preview deployment origins explicitly when testing Vercel preview URLs.

Verify CORS from the deployed frontend origin:

```bash
curl -i -X OPTIONS https://ledgerstream-backend-5rk9.onrender.com/api/auth/register \
  -H 'Origin: https://ledger-stream.vercel.app' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,authorization,idempotency-key'
```

Expected result after the Render env var is correct: `200` or `204` with `access-control-allow-origin: https://ledger-stream.vercel.app`.

## Health Checks

| Service | Path | Expected |
| --- | --- | --- |
| Backend | `/actuator/health` | `{"status":"UP"}` |
| Backend metrics | `/actuator/prometheus` | Prometheus text format |
| Frontend container | `/health` | `ok` |
| Frontend Vercel | `/` | Dashboard shell loads |

Render should use `/actuator/health` as the health check path. Vercel does not need a health check path for static hosting.

Recommended live smoke checks:

```bash
curl -i https://ledgerstream-backend-5rk9.onrender.com/actuator/health
curl -i https://ledgerstream-backend-5rk9.onrender.com/api/ping
curl -I https://ledger-stream.vercel.app/
```

For an authenticated API smoke test, register a temporary user through `/api/auth/register`, call `/api/me`, `/api/symbols`, `/api/portfolio`, `/api/portfolio/positions`, and `/api/portfolio/ledger`, then revoke the refresh token with `/api/auth/logout`. Do not print or store returned access or refresh tokens.

## Rollback Notes

- Frontend: use Vercel's deployment history to promote the previous successful deployment.
- Backend: use Render's rollback or redeploy the previous image/commit.
- Database: avoid destructive migrations. For schema changes, prefer additive migrations and deploy code that can read both old and new shapes before removing columns.
- Redis: latest quote cache can be flushed and rebuilt from new ticks; do not treat Redis as accounting source of truth.
- Kafka: keep JSON event changes backward compatible until all consumers are updated.

If a deployment fails after Flyway applies a migration, roll forward with a fix rather than manually editing production schema.
