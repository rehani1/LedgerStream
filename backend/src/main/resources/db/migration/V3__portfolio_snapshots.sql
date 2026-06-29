CREATE TABLE portfolio_snapshots (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    total_equity NUMERIC(18,2) NOT NULL,
    cash NUMERIC(18,2) NOT NULL,
    market_value NUMERIC(18,2) NOT NULL,
    gross_exposure NUMERIC(18,2) NOT NULL,
    realized_pnl NUMERIC(18,2) NOT NULL,
    unrealized_pnl NUMERIC(18,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_portfolio_snapshots_gross_exposure_non_negative CHECK (gross_exposure >= 0)
);

CREATE INDEX idx_portfolio_snapshots_user_created_desc ON portfolio_snapshots(user_id, created_at DESC);
