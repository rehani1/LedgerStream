from io import StringIO
from pathlib import Path

from ledgerstream_market_data.replay import replay_market_ticks


def test_replay_market_ticks_dry_run_writes_json_lines(tmp_path: Path) -> None:
	csv_file = tmp_path / "ticks.csv"
	csv_file.write_text(
		"timestamp,symbol,bid,ask,last,volume,source\n"
		"2026-01-02T14:30:00Z,AAPL,187.120000,187.180000,187.150000,125000,fixture\n"
		"2026-01-02T14:31:00Z,AAPL,187.260000,187.340000,187.300000,132500,fixture\n",
		encoding="utf-8",
	)
	output = StringIO()

	count = replay_market_ticks(
		csv_file,
		topic="market.tick",
		bootstrap_servers="localhost:19092",
		speed=1.0,
		dry_run=True,
		output=output,
	)

	assert count == 2
	lines = output.getvalue().splitlines()
	assert len(lines) == 2
	assert '"symbol":"AAPL"' in lines[0]
	assert '"last":187.300000' in lines[1]


def test_replay_market_ticks_publishes_to_kafka(monkeypatch, tmp_path: Path) -> None:
	csv_file = tmp_path / "ticks.csv"
	csv_file.write_text(
		"timestamp,symbol,bid,ask,last,volume,source\n"
		"2026-01-02T14:30:00Z,AAPL,187.120000,187.180000,187.150000,125000,fixture\n",
		encoding="utf-8",
	)
	publish_calls = []
	flush_calls = []
	publisher_kwargs = []

	class FakePublisher:
		def __init__(self, bootstrap_servers: str, **kwargs) -> None:
			self.bootstrap_servers = bootstrap_servers
			self.kwargs = kwargs
			publisher_kwargs.append(kwargs)

		def publish(self, topic: str, key: str, payload: str) -> None:
			publish_calls.append((topic, key, payload))

		def flush(self, timeout_seconds: float) -> None:
			flush_calls.append(timeout_seconds)

	monkeypatch.setattr("ledgerstream_market_data.replay.KafkaMarketTickPublisher", FakePublisher)

	count = replay_market_ticks(
		csv_file,
		topic="market.tick",
		bootstrap_servers="redpanda:9092",
		speed=100.0,
		kafka_security_protocol="SASL_SSL",
		kafka_sasl_mechanism="SCRAM-SHA-256",
		kafka_sasl_username="worker",
		kafka_sasl_password="worker-password",
		dry_run=False,
		flush_timeout_seconds=4,
	)

	assert count == 1
	assert publish_calls[0][0] == "market.tick"
	assert publish_calls[0][1] == "AAPL"
	assert '"last":187.150000' in publish_calls[0][2]
	assert publisher_kwargs == [
		{
			"security_protocol": "SASL_SSL",
			"sasl_mechanism": "SCRAM-SHA-256",
			"sasl_username": "worker",
			"sasl_password": "worker-password",
		}
	]
	assert flush_calls == [4]


def test_replay_market_ticks_uses_scaled_delays_without_sleeping(monkeypatch, tmp_path: Path) -> None:
	csv_file = tmp_path / "ticks.csv"
	csv_file.write_text(
		"timestamp,symbol,bid,ask,last,volume,source\n"
		"2026-01-02T14:30:00Z,AAPL,187.120000,187.180000,187.150000,125000,fixture\n"
		"2026-01-02T14:30:04Z,AAPL,187.260000,187.340000,187.300000,132500,fixture\n",
		encoding="utf-8",
	)
	publish_calls = []
	sleep_calls = []

	class FakePublisher:
		def __init__(self, bootstrap_servers: str, **kwargs) -> None:
			self.bootstrap_servers = bootstrap_servers

		def publish(self, topic: str, key: str, payload: str) -> None:
			publish_calls.append((topic, key, payload))

		def flush(self, timeout_seconds: float) -> None:
			pass

	def fake_sleep(delay_seconds: float) -> None:
		sleep_calls.append(delay_seconds)

	monkeypatch.setattr("ledgerstream_market_data.replay.KafkaMarketTickPublisher", FakePublisher)

	count = replay_market_ticks(
		csv_file,
		topic="market.tick",
		bootstrap_servers="redpanda:9092",
		speed=2.0,
		dry_run=False,
		sleeper=fake_sleep,
	)

	assert count == 2
	assert sleep_calls == [2.0]
	assert [call[1] for call in publish_calls] == ["AAPL", "AAPL"]
