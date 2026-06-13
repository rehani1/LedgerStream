INSERT INTO symbols (id, ticker, name, exchange, asset_type, currency, active, created_at)
VALUES
    ('2f86ad08-9b6b-45f4-a7f6-0f4eab1de001', 'AAPL', 'Apple Inc.', 'NASDAQ', 'EQUITY', 'USD', TRUE, now()),
    ('2f86ad08-9b6b-45f4-a7f6-0f4eab1de002', 'MSFT', 'Microsoft Corporation', 'NASDAQ', 'EQUITY', 'USD', TRUE, now()),
    ('2f86ad08-9b6b-45f4-a7f6-0f4eab1de003', 'NVDA', 'NVIDIA Corporation', 'NASDAQ', 'EQUITY', 'USD', TRUE, now()),
    ('2f86ad08-9b6b-45f4-a7f6-0f4eab1de004', 'TSLA', 'Tesla, Inc.', 'NASDAQ', 'EQUITY', 'USD', TRUE, now()),
    ('2f86ad08-9b6b-45f4-a7f6-0f4eab1de005', 'SPY', 'SPDR S&P 500 ETF Trust', 'NYSEARCA', 'ETF', 'USD', TRUE, now())
ON CONFLICT (ticker) DO NOTHING;
