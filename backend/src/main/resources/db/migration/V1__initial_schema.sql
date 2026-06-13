CREATE TABLE users (
    id UUID PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_updated_after_created CHECK (updated_at >= created_at)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_refresh_tokens_revoked_after_created CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT chk_refresh_tokens_expires_after_created CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

CREATE TABLE symbols (
    id UUID PRIMARY KEY,
    ticker TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    exchange TEXT NOT NULL,
    asset_type TEXT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_symbols_ticker_not_blank CHECK (length(trim(ticker)) > 0),
    CONSTRAINT chk_symbols_ticker_uppercase CHECK (ticker = upper(ticker)),
    CONSTRAINT chk_symbols_asset_type CHECK (asset_type IN ('EQUITY', 'ETF')),
    CONSTRAINT chk_symbols_currency CHECK (currency = upper(currency) AND length(currency) = 3)
);

CREATE INDEX idx_symbols_active_ticker ON symbols(active, ticker);

CREATE TABLE price_ticks (
    id BIGSERIAL PRIMARY KEY,
    symbol_id UUID NOT NULL REFERENCES symbols(id),
    ts TIMESTAMPTZ NOT NULL,
    bid NUMERIC(18,6) NULL,
    ask NUMERIC(18,6) NULL,
    last NUMERIC(18,6) NOT NULL,
    volume BIGINT NULL,
    source TEXT NOT NULL,
    CONSTRAINT chk_price_ticks_bid_positive CHECK (bid IS NULL OR bid > 0),
    CONSTRAINT chk_price_ticks_ask_positive CHECK (ask IS NULL OR ask > 0),
    CONSTRAINT chk_price_ticks_last_positive CHECK (last > 0),
    CONSTRAINT chk_price_ticks_volume_non_negative CHECK (volume IS NULL OR volume >= 0),
    CONSTRAINT chk_price_ticks_source_not_blank CHECK (length(trim(source)) > 0)
);

CREATE INDEX idx_price_ticks_symbol_ts_desc ON price_ticks(symbol_id, ts DESC);
CREATE UNIQUE INDEX uq_price_ticks_symbol_ts_source ON price_ticks(symbol_id, ts, source);

CREATE TABLE portfolios (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    cash_balance NUMERIC(18,2) NOT NULL,
    base_currency TEXT NOT NULL DEFAULT 'USD',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_portfolios_cash_non_negative CHECK (cash_balance >= 0),
    CONSTRAINT chk_portfolios_base_currency CHECK (base_currency = upper(base_currency) AND length(base_currency) = 3),
    CONSTRAINT chk_portfolios_updated_after_created CHECK (updated_at >= created_at)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    symbol_id UUID NOT NULL REFERENCES symbols(id),
    side TEXT NOT NULL,
    order_type TEXT NOT NULL,
    quantity NUMERIC(18,6) NOT NULL,
    limit_price NUMERIC(18,6) NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    rejection_reason TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_orders_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_orders_order_type CHECK (order_type IN ('MARKET', 'LIMIT')),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'FILLED', 'CANCELLED', 'REJECTED')),
    CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_orders_limit_price_positive CHECK (limit_price IS NULL OR limit_price > 0),
    CONSTRAINT chk_orders_market_limit_price CHECK (
        (order_type = 'MARKET' AND limit_price IS NULL)
        OR (order_type = 'LIMIT' AND limit_price IS NOT NULL)
    ),
    CONSTRAINT chk_orders_idempotency_key_not_blank CHECK (length(trim(idempotency_key)) > 0),
    CONSTRAINT chk_orders_rejected_reason CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL)
        OR (status <> 'REJECTED')
    ),
    CONSTRAINT chk_orders_updated_after_created CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_orders_user_idempotency_key ON orders(user_id, idempotency_key);
CREATE INDEX idx_orders_user_created_desc ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_symbol_status ON orders(symbol_id, status);

CREATE TABLE fills (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    symbol_id UUID NOT NULL REFERENCES symbols(id),
    price NUMERIC(18,6) NOT NULL,
    quantity NUMERIC(18,6) NOT NULL,
    fee NUMERIC(18,2) NOT NULL DEFAULT 0,
    filled_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_fills_price_positive CHECK (price > 0),
    CONSTRAINT chk_fills_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_fills_fee_non_negative CHECK (fee >= 0)
);

CREATE INDEX idx_fills_order_id ON fills(order_id);
CREATE INDEX idx_fills_symbol_filled_at ON fills(symbol_id, filled_at DESC);

CREATE TABLE positions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    symbol_id UUID NOT NULL REFERENCES symbols(id),
    quantity NUMERIC(18,6) NOT NULL,
    avg_cost NUMERIC(18,6) NOT NULL,
    realized_pnl NUMERIC(18,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_positions_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT chk_positions_avg_cost_non_negative CHECK (avg_cost >= 0)
);

CREATE UNIQUE INDEX uq_positions_user_symbol ON positions(user_id, symbol_id);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    order_id UUID NULL REFERENCES orders(id),
    fill_id UUID NULL REFERENCES fills(id),
    entry_type TEXT NOT NULL,
    cash_delta NUMERIC(18,2) NOT NULL DEFAULT 0,
    symbol_id UUID NULL REFERENCES symbols(id),
    quantity_delta NUMERIC(18,6) NOT NULL DEFAULT 0,
    price NUMERIC(18,6) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NULL,
    CONSTRAINT chk_ledger_entries_type CHECK (
        entry_type IN ('INITIAL_DEPOSIT', 'BUY_FILL', 'SELL_FILL', 'FEE', 'REVERSAL', 'ADJUSTMENT')
    ),
    CONSTRAINT chk_ledger_entries_price_positive CHECK (price IS NULL OR price > 0)
);

CREATE INDEX idx_ledger_entries_user_created_desc ON ledger_entries(user_id, created_at DESC);
CREATE INDEX idx_ledger_entries_order_id ON ledger_entries(order_id);

CREATE TABLE risk_snapshots (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    total_equity NUMERIC(18,2) NOT NULL,
    cash NUMERIC(18,2) NOT NULL,
    gross_exposure NUMERIC(18,2) NOT NULL,
    largest_position_pct NUMERIC(8,4) NOT NULL,
    unrealized_pnl NUMERIC(18,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_risk_snapshots_gross_exposure_non_negative CHECK (gross_exposure >= 0),
    CONSTRAINT chk_risk_snapshots_largest_position_pct_non_negative CHECK (largest_position_pct >= 0)
);

CREATE INDEX idx_risk_snapshots_user_created_desc ON risk_snapshots(user_id, created_at DESC);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    user_id UUID NULL REFERENCES users(id),
    action TEXT NOT NULL,
    request_id TEXT NULL,
    ip_hash TEXT NULL,
    user_agent TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NULL,
    CONSTRAINT chk_audit_events_action_not_blank CHECK (length(trim(action)) > 0)
);

CREATE INDEX idx_audit_events_user_created_desc ON audit_events(user_id, created_at DESC);
CREATE INDEX idx_audit_events_action_created_desc ON audit_events(action, created_at DESC);
