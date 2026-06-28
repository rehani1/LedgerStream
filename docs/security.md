# Security

## Scope

LedgerStream is a paper-trading system only. It must not place real brokerage orders, commit real secrets, or log sensitive tokens, passwords, API keys, or raw personal data.

## Threat Model

| Threat | Control |
| --- | --- |
| Credential stuffing or brute-force login attempts | Per-instance fixed-window rate limits on login and registration, generic login failure responses, BCrypt password hashing. |
| Stolen refresh token reuse | Refresh tokens are opaque, hashed at rest, rotated on every use, and reuse of a revoked token revokes remaining active tokens for that user. |
| Cross-user financial data access | Financial reads and mutations use the authenticated user ID in repository predicates; missing or cross-user resources return `404`. |
| Unauthorized admin actions | `/api/admin/**` is protected by `ADMIN` role checks in the security filter chain and `@PreAuthorize`. |
| Duplicate order submission | `orders(user_id, idempotency_key)` is unique; duplicate requests return the existing order and do not create duplicate fills. |
| Token or password disclosure through logs | Request logs exclude bodies, query strings, authorization headers, cookies, tokens, passwords, and raw client IPs. |
| Malformed market data | Tick ingestion validates symbol, timestamp, positive prices, non-negative volume, and bid/ask ordering before cache or persistence updates. |
| Vulnerable dependencies | Dependabot monitors Maven, npm, and Python dependencies; Dependency Review blocks pull requests that add high-severity vulnerable dependencies. |

## Auth Model

- Passwords are hashed with BCrypt before storage.
- JWT access tokens are signed with HMAC SHA-256 through Spring Security JOSE.
- Access tokens include the user ID as `sub`, plus email and role claims.
- Refresh tokens are random opaque values returned only at issue time.
- The backend stores only SHA-256 refresh-token hashes.
- Registration creates a user, a zero-cash portfolio, an audit event, and an access/refresh token pair.
- Login returns an access/refresh token pair for valid credentials.
- Refresh requires the current refresh token, revokes it, and issues a new token pair.
- Logout revokes the provided refresh token and is idempotent for unknown tokens.
- Invalid login attempts return the same generic `401` response whether or not the email exists.

## Authorization Model

- Public endpoints are explicitly listed in `SecurityConfiguration`.
- Every non-auth API endpoint requires authentication unless explicitly permitted.
- `/api/admin/**` requires `ADMIN`; normal users receive `403`.
- Order, portfolio, position, ledger, and risk paths are user-scoped through authenticated principal IDs.
- Missing or cross-user financial resources are reported as `404` to avoid leaking another user's resource existence.
- Admin replay controls record audit events and do not accept shell commands, worker paths, or arbitrary process arguments.

## Token Storage Tradeoff

Refresh tokens are currently returned in response bodies for API and testability. The frontend stores the access token, refresh token, expirations, and current user in `sessionStorage` for the MVP. This avoids persistence across browser restarts but is still readable by JavaScript if an XSS bug exists.

A production deployment should move refresh tokens to `Secure`, `HttpOnly`, `SameSite` cookies and keep access tokens in memory where practical. The frontend verifies a stored session with `GET /api/me` during startup, attempts one refresh-token rotation when a stored access token is rejected, and clears local session state on refresh failure.

## Rate Limiting

The backend applies configurable fixed-window limits before sensitive controller actions. Defaults are:

| Policy | Default |
| --- | --- |
| Login | `5/min` |
| Registration | `3/10m` |
| Order creation | `60/min` |
| Quote stream creation | `20/min` |

Exceeded limits return the standard API error shape with status `429`, `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset` headers.

Limits can be changed with `BACKEND_RATE_LIMIT_*` environment variables, including global enablement, per-policy enablement, maximum requests, and window duration. Authenticated limits use the user ID. Anonymous auth limits use a hashed client network hint; raw IP addresses are not persisted by the limiter.

## Input Validation

- Public request DTOs use Jakarta validation for required fields and basic shape.
- Order service validation normalizes ticker symbols, validates idempotency key format, rejects non-positive quantities, and enforces market versus limit price rules.
- Database constraints enforce enum values, positive prices and quantities, unique idempotency keys, unique replay ticks, and rejected-order reasons.
- Request IDs are accepted only when they match a safe bounded pattern; otherwise the backend generates a UUID.
- CORS origins, methods, headers, exposed headers, credentials, and max age are environment-driven through backend configuration.

## Audit Logging

The backend persists audit rows for registration, login success, safe login failure reasons, refresh-token rotation, logout, order creation, order cancellation, order rejection, and admin replay start or stop. Audit rows include the current request ID when the action originates from an HTTP request.

Audit metadata is limited to operational identifiers and safe state such as order ID, symbol, order side/type, quantity, and rejection reason. Passwords, access tokens, refresh tokens, API keys, and raw IP addresses are not stored in audit metadata.

## Secrets Management

- `.env.example` and `application.yml` use non-secret placeholders only.
- Local `.env` files must not be committed.
- Production values for `BACKEND_JWT_SECRET`, database credentials, Redis credentials, broker credentials, and deployment tokens must come from the hosting platform secret manager.
- Demo account seeding is disabled by default and only available under `local` or `dev` profiles when explicitly enabled.
- Seed passwords are read from environment configuration, hashed with BCrypt before storage, and never logged.

## Dependency And Repository Scanning

- `.github/dependabot.yml` schedules weekly dependency checks for Maven, npm, and Python requirements.
- `.github/workflows/dependency-review.yml` reviews pull-request dependency changes and fails on high-severity vulnerabilities.
- The repository verification command `git grep -n "password\|secret\|token\|api_key\|apikey" -- . ':!agents.md' ':!AGENTS.md'` is used during security review to inspect likely sensitive strings.
- Matches in the current tree are placeholders, configuration keys, DTO fields, test fixtures, or documentation examples; no real secret values were found in this pass.

## Known Limitations

- Rate limiting is in-memory per backend instance. Multi-instance deployment needs Redis-backed distributed counters.
- Refresh tokens are stored in frontend `sessionStorage` for the MVP. HttpOnly cookie storage is the preferred production improvement.
- Kafka publishes are not backed by an outbox table yet, so event recovery after a crash is not guaranteed.
- SSE subscription state is in memory. Multi-instance deployments need sticky routing or shared fanout.
- Dependency Review runs on pull requests; direct pushes still rely on CI, Dependabot alerts, and code review discipline.
