from pathlib import Path

from ledgerstream_market_data.settings import MarketDataSettings


def test_settings_read_environment(monkeypatch) -> None:
	monkeypatch.setenv("MARKET_DATA_KAFKA_BOOTSTRAP_SERVERS", "redpanda:9092")
	monkeypatch.setenv("MARKET_DATA_TICK_TOPIC", "market.tick")
	monkeypatch.setenv("MARKET_DATA_REPLAY_SPEED", "1.5")
	monkeypatch.setenv("MARKET_DATA_FILE", "data/sample_ticks.csv")
	monkeypatch.setenv("MARKET_DATA_LOG_LEVEL", "DEBUG")
	monkeypatch.setenv("MARKET_DATA_DRY_RUN", "true")
	monkeypatch.setenv("MARKET_DATA_PRODUCER_FLUSH_TIMEOUT_SECONDS", "3")

	settings = MarketDataSettings.from_env()

	assert settings.kafka_bootstrap_servers == "redpanda:9092"
	assert settings.tick_topic == "market.tick"
	assert settings.replay_speed == 1.5
	assert settings.data_path == Path("data/sample_ticks.csv")
	assert settings.log_level == "DEBUG"
	assert settings.dry_run is True
	assert settings.producer_flush_timeout_seconds == 3
