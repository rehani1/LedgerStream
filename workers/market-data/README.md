# LedgerStream Market Data Worker

Python worker for deterministic market-data replay and future Kafka-compatible `market.tick` publishing.

## Local Commands

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
PYTHONPATH=src pytest
PYTHONPATH=src python -m ledgerstream_market_data replay --file data/sample_ticks.csv --dry-run
```

The included `data/sample_ticks.csv` fixture contains 25 deterministic ticks across `AAPL`, `MSFT`, `NVDA`, `TSLA`, and `SPY`. The replay command validates the CSV schema and row values. `--dry-run` prints JSON payloads without Kafka; omit it to publish `market.tick` events to the configured Redpanda/Kafka broker.

For hosted Kafka brokers that require TLS and SASL, set:

```bash
MARKET_DATA_KAFKA_BOOTSTRAP_SERVERS=<broker-host:port>
MARKET_DATA_KAFKA_SECURITY_PROTOCOL=SASL_SSL
MARKET_DATA_KAFKA_SASL_MECHANISM=SCRAM-SHA-256
MARKET_DATA_KAFKA_SASL_USERNAME=<service-account>
MARKET_DATA_KAFKA_SASL_PASSWORD=<service-secret>
```

## Tests

The CI-friendly worker test command is:

```bash
PYTHONPATH=src pytest
```

Worker tests cover CSV parsing, invalid row context, deterministic event serialization, dry-run replay output, Kafka publish calls, and replay timing calculations with an injected sleeper so tests do not actually wait.
