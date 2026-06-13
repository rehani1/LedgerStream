# Deployment

## Target

The MVP deployment path will use managed services rather than Kubernetes. Acceptable targets include Vercel for the frontend, Fly.io or Render for the backend, Neon or Supabase for PostgreSQL, Upstash for Redis, and Redpanda Cloud or another Kafka-compatible hosted stream.

## Local Deployment

The target local workflow is:

```bash
docker compose up --build
```

The frontend image is built from `frontend/Dockerfile` and served by Nginx on the configured `FRONTEND_PORT`, defaulting to `5173`. Docker Compose passes `FRONTEND_API_BASE_URL` as the frontend build-time API base URL; the default is `http://localhost:8080` so browser requests reach the locally exposed backend.

## Environment Variables

See [.env.example](../.env.example) for non-secret local placeholders. Production deployments must provide real values through the hosting platform secret manager.

Frontend-specific local variables are also documented in `frontend/.env.example`.

## TODO

- Select final public demo hosting path.
- Document backend health checks.
- Document database migration command.
- Document Redis and event-stream configuration.
- Document CORS configuration.
- Document rollback procedure.
- Add live demo links when deployed.
