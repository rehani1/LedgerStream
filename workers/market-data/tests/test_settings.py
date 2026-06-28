from pathlib import Path

from ledgerstream_market_data.settings import MarketDataSettings


def test_settings_read_environment(monkeypatch) -> None:
	monkeypatch.setenv("MARKET_DATA_KAFKA_BOOTSTRAP_SERVERS", "redpanda:9092")
	monkeypatch.setenv("MARKET_DATA_KAFKA_SECURITY_PROTOCOL", "SASL_SSL")
	monkeypatch.setenv("MARKET_DATA_KAFKA_SASL_MECHANISM", "SCRAM-SHA-256")
	monkeypatch.setenv("MARKET_DATA_KAFKA_SASL_USERNAME", "worker")
	monkeypatch.setenv("MARKET_DATA_KAFKA_SASL_PASSWORD", "worker-password")
	monkeypatch.setenv("MARKET_DATA_TICK_TOPIC", "market.tick")
	monkeypatch.setenv("MARKET_DATA_REPLAY_SPEED", "1.5")
	monkeypatch.setenv("MARKET_DATA_FILE", "data/sample_ticks.csv")
	monkeypatch.setenv("MARKET_DATA_LOG_LEVEL", "DEBUG")
	monkeypatch.setenv("MARKET_DATA_DRY_RUN", "true")
	monkeypatch.setenv("MARKET_DATA_PRODUCER_FLUSH_TIMEOUT_SECONDS", "3")

	settings = MarketDataSettings.from_env()

	assert settings.kafka_bootstrap_servers == "redpanda:9092"
	assert settings.kafka_security_protocol == "SASL_SSL"
	assert settings.kafka_sasl_mechanism == "SCRAM-SHA-256"
	assert settings.kafka_sasl_username == "worker"
	assert settings.kafka_sasl_password == "worker-password"
	assert settings.tick_topic == "market.tick"
	assert settings.replay_speed == 1.5
	assert settings.data_path == Path("data/sample_ticks.csv")
	assert settings.log_level == "DEBUG"
	assert settings.dry_run is True
	assert settings.producer_flush_timeout_seconds == 3
