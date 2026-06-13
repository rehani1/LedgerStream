# Security

## Scope

LedgerStream is a paper-trading system only. It must not place real brokerage orders, commit real secrets, or log sensitive tokens, passwords, API keys, or raw personal data.

## Planned Controls

- Password hashing with BCrypt. Implemented for registration and demo seeding.
- JWT access tokens. Implemented with HMAC SHA-256 signing through Spring Security JOSE.
- Refresh token rotation.
- Hashed refresh token storage.
- Role-based authorization with `USER` and `ADMIN` roles.
- User-scoped ownership checks for orders, positions, portfolio, ledger, and risk data.
- Restricted CORS based on configured frontend origin.
- Input validation for all public request bodies.
- Rate limiting for authentication and order creation.
- Audit events for security and financial actions.
- Sanitized structured logs with request IDs.

## Demo Credentials

Demo account seeding is disabled by default and only available under `local` or `dev` Spring profiles when explicitly enabled. The seed password is read from environment configuration, hashed with BCrypt before storage, and never logged.

## Implemented Auth Model

- `POST /api/auth/register` normalizes email, rejects duplicate email with `409`, stores a BCrypt password hash, creates a zero-cash portfolio, records an audit event, and returns a JWT access token.
- `POST /api/auth/login` returns a JWT access token for valid credentials.
- Invalid login attempts return the same generic `401` response regardless of whether the email exists.
- `GET /api/me` requires a valid authenticated principal.
- Access tokens include user ID as `sub`, plus email and role claims.

Refresh token rotation is not implemented yet; it is tracked as the next auth hardening increment.

## Token Storage Tradeoff

The final frontend implementation will document whether refresh tokens use secure cookies or another MVP-compatible strategy. Any fallback tradeoff must be explicit.

## TODO

- Add threat model.
- Add concrete auth flow once implemented.
- Add authorization test coverage notes.
- Add dependency scanning configuration.
- Add secrets-management instructions.
- Add known limitations.
