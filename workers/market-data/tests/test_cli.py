from pathlib import Path

import pytest

from ledgerstream_market_data.cli import ReplayCommandError, build_parser, positive_float, run_replay
from ledgerstream_market_data.settings import MarketDataSettings


def test_replay_parser_uses_settings_defaults() -> None:
	settings = MarketDataSettings(
		kafka_bootstrap_servers="localhost:19092",
		tick_topic="market.tick",
		replay_speed=2.0,
		data_path=Path("data/sample_ticks.csv"),
		log_level="DEBUG",
	)

	args = build_parser(settings).parse_args(["replay"])

	assert args.log_level == "DEBUG"
	assert args.file == "data/sample_ticks.csv"
	assert args.bootstrap_servers == "localhost:19092"
	assert args.topic == "market.tick"
	assert args.speed == 2.0


def test_positive_float_rejects_zero() -> None:
	with pytest.raises(Exception, match="greater than zero"):
		positive_float("0")


def test_run_replay_rejects_missing_file(tmp_path: Path) -> None:
	settings = MarketDataSettings(
		kafka_bootstrap_servers="localhost:19092",
		tick_topic="market.tick",
		replay_speed=1.0,
		data_path=tmp_path / "missing.csv",
		log_level="INFO",
	)
	args = build_parser(settings).parse_args(["replay"])

	with pytest.raises(ReplayCommandError, match="replay file not found"):
		run_replay(args)


def test_run_replay_accepts_existing_file(tmp_path: Path) -> None:
	replay_file = tmp_path / "ticks.csv"
	replay_file.write_text("timestamp,symbol,bid,ask,last,volume,source\n", encoding="utf-8")
	settings = MarketDataSettings(
		kafka_bootstrap_servers="localhost:19092",
		tick_topic="market.tick",
		replay_speed=1.0,
		data_path=replay_file,
		log_level="INFO",
	)
	args = build_parser(settings).parse_args(["replay"])

	assert run_replay(args) == 0
