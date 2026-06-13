# Security

## Scope

LedgerStream is a paper-trading system only. It must not place real brokerage orders, commit real secrets, or log sensitive tokens, passwords, API keys, or raw personal data.

## Planned Controls

- Password hashing with BCrypt or Argon2.
- JWT access tokens and refresh token rotation.
- Hashed refresh token storage.
- Role-based authorization with `USER` and `ADMIN` roles.
- User-scoped ownership checks for orders, positions, portfolio, ledger, and risk data.
- Restricted CORS based on configured frontend origin.
- Input validation for all public request bodies.
- Rate limiting for authentication and order creation.
- Audit events for security and financial actions.
- Sanitized structured logs with request IDs.

## Token Storage Tradeoff

The final frontend implementation will document whether refresh tokens use secure cookies or another MVP-compatible strategy. Any fallback tradeoff must be explicit.

## TODO

- Add threat model.
- Add concrete auth flow once implemented.
- Add authorization test coverage notes.
- Add dependency scanning configuration.
- Add secrets-management instructions.
- Add known limitations.
