# LedgerStream Market Data Worker

Python worker for deterministic market-data replay and future Kafka-compatible `market.tick` publishing.

## Local Commands

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
pytest
PYTHONPATH=src python -m ledgerstream_market_data replay --file data/sample_ticks.csv --dry-run
```

The included `data/sample_ticks.csv` fixture contains 25 deterministic ticks across `AAPL`, `MSFT`, `NVDA`, `TSLA`, and `SPY`. The replay command validates the CSV schema and row values. `--dry-run` prints JSON payloads without Kafka; omit it to publish `market.tick` events to the configured Redpanda/Kafka broker.
