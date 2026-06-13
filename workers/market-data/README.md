# LedgerStream Market Data Worker

Python worker for deterministic market-data replay and future Kafka-compatible `market.tick` publishing.

## Local Commands

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
pytest
python -m ledgerstream_market_data replay --file data/sample_ticks.csv
```

The replay command validates configuration and input paths in this skeleton. CSV parsing and Kafka publishing are implemented in the next worker increments.
