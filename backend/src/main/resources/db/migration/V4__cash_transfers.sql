ALTER TABLE ledger_entries DROP CONSTRAINT chk_ledger_entries_type;

ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_entries_type CHECK (
    entry_type IN (
        'INITIAL_DEPOSIT',
        'CASH_DEPOSIT',
        'CASH_WITHDRAWAL',
        'BUY_FILL',
        'SELL_FILL',
        'FEE',
        'REVERSAL',
        'ADJUSTMENT'
    )
);

CREATE TABLE cash_transfers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    ledger_entry_id UUID NULL REFERENCES ledger_entries(id),
    transfer_type TEXT NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    note TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_cash_transfers_type CHECK (transfer_type IN ('DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT chk_cash_transfers_status CHECK (status IN ('COMPLETED')),
    CONSTRAINT chk_cash_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_cash_transfers_idempotency_key_not_blank CHECK (length(trim(idempotency_key)) > 0)
);

CREATE UNIQUE INDEX uq_cash_transfers_user_idempotency_key ON cash_transfers(user_id, idempotency_key);
CREATE UNIQUE INDEX uq_cash_transfers_ledger_entry_id ON cash_transfers(ledger_entry_id) WHERE ledger_entry_id IS NOT NULL;
CREATE INDEX idx_cash_transfers_user_created_desc ON cash_transfers(user_id, created_at DESC);
