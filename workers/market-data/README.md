# LedgerStream Market Data Worker

Python worker for deterministic market-data replay and future Kafka-compatible `market.tick` publishing.

## Local Commands

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
pytest
PYTHONPATH=src python -m ledgerstream_market_data replay --file data/sample_ticks.csv
```

The included `data/sample_ticks.csv` fixture contains 25 deterministic ticks across `AAPL`, `MSFT`, `NVDA`, `TSLA`, and `SPY`. The replay command validates the CSV schema and row values. Kafka publishing is implemented in the next worker increment.
