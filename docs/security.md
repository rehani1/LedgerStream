# Security

## Scope

LedgerStream is a paper-trading system only. It must not place real brokerage orders, commit real secrets, or log sensitive tokens, passwords, API keys, or raw personal data.

## Planned Controls

- Password hashing with BCrypt. Implemented for registration and demo seeding.
- JWT access tokens. Implemented with HMAC SHA-256 signing through Spring Security JOSE.
- Refresh token rotation. Implemented for register, login, and refresh flows.
- Hashed refresh token storage. Implemented with opaque refresh tokens and stored SHA-256 hashes.
- Role-based authorization with `USER` and `ADMIN` roles. Implemented for admin route protection.
- User-scoped ownership checks for orders, positions, portfolio, ledger, and risk data. Implemented as reusable backend access-control helpers for future financial controllers.
- Restricted CORS based on configured frontend origin.
- Input validation for all public request bodies.
- Rate limiting for authentication, order creation, and quote-stream subscription creation. Implemented with configurable fixed-window policies.
- Audit events for security, financial, and admin replay-control actions.
- Sanitized structured logs with request IDs.

## Demo Credentials

Demo account seeding is disabled by default and only available under `local` or `dev` Spring profiles when explicitly enabled. The seed password is read from environment configuration, hashed with BCrypt before storage, and never logged.

## Implemented Auth Model

- `POST /api/auth/register` normalizes email, rejects duplicate email with `409`, stores a BCrypt password hash, creates a zero-cash portfolio, records an audit event, and returns an access/refresh token pair.
- `POST /api/auth/login` returns an access/refresh token pair for valid credentials.
- `POST /api/auth/refresh` requires the current refresh token, revokes it, and returns a new access/refresh token pair.
- Refresh token reuse is rejected and triggers revocation of remaining active refresh tokens for the user.
- `POST /api/auth/logout` revokes the provided refresh token and is idempotent for unknown tokens.
- Invalid login attempts return the same generic `401` response regardless of whether the email exists.
- `GET /api/me` requires a valid authenticated principal.
- Access tokens include user ID as `sub`, plus email and role claims.

## Implemented Authorization Model

- Every non-auth API endpoint requires authentication unless explicitly marked public.
- `/api/admin/**` requires `ADMIN`; normal users receive `403`.
- Ownership helpers verify order, portfolio, position, ledger, and risk snapshot access through user-scoped repository checks.
- Missing or cross-user financial resources are reported as `404` to avoid leaking another user's resource existence.
- Admin replay start and stop controls record audit events and do not accept user-controlled worker commands or shell arguments.

## Implemented Audit Events

The backend persists audit rows for registration, login success, safe login failure reasons, refresh-token rotation, logout, order creation, order cancellation, order rejection, and admin replay start or stop. Audit rows include the current request ID when the action originates from an HTTP request. Order rejection audits preserve the original order-submission request ID by carrying it on the internal `order.created` event.

Audit metadata is limited to operational identifiers and state such as order ID, symbol, order side/type, quantity, and safe rejection reason. Passwords, access tokens, refresh tokens, API keys, and raw IP addresses are not stored in audit metadata. The application currently omits IP address collection rather than storing raw network identifiers.

## Implemented Rate Limits

The backend applies per-instance fixed-window limits before sensitive controller actions. Defaults are `5/min` for login, `3/10m` for registration, `60/min` for order creation, and `20/min` for quote stream creation. Exceeded limits return the standard API error shape with status `429` and retry headers.

Limits can be changed with `BACKEND_RATE_LIMIT_*` environment variables, including `BACKEND_RATE_LIMIT_ENABLED`, per-policy `*_ENABLED`, `*_MAX_REQUESTS`, and `*_WINDOW` values. Authenticated limits use the user ID. Anonymous auth limits use a hashed client network hint; raw IP addresses are not stored in audit metadata or persisted by the limiter.

## Token Storage Tradeoff

Refresh tokens are currently returned in response bodies for API and testability. The frontend stores the access token, refresh token, expirations, and current user in `sessionStorage` for the MVP. This avoids persistence across browser restarts but is still readable by JavaScript if an XSS bug exists. A production deployment should move refresh tokens to `Secure`, `HttpOnly`, `SameSite` cookies and keep access tokens in memory where practical.

The frontend verifies a stored session with `GET /api/me` during app startup. If the access token is rejected, it attempts one refresh-token rotation and clears the session on failure. Logout clears local session state first and then calls the backend logout endpoint to revoke the refresh token.

## TODO

- Add threat model.
- Add concrete auth flow once implemented.
- Add dependency scanning configuration.
- Add secrets-management instructions.
- Add known limitations.
