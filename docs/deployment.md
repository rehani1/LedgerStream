# Deployment

## Target

The MVP deployment path will use managed services rather than Kubernetes. Acceptable targets include Vercel for the frontend, Fly.io or Render for the backend, Neon or Supabase for PostgreSQL, Upstash for Redis, and Redpanda Cloud or another Kafka-compatible hosted stream.

## Local Deployment

The target local workflow is:

```bash
docker compose up --build
```

## Environment Variables

See [.env.example](../.env.example) for non-secret local placeholders. Production deployments must provide real values through the hosting platform secret manager.

## TODO

- Select final public demo hosting path.
- Document backend health checks.
- Document database migration command.
- Document Redis and event-stream configuration.
- Document CORS configuration.
- Document rollback procedure.
- Add live demo links when deployed.
