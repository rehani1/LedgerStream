import json
from decimal import Decimal
from pathlib import Path

import pytest

from ledgerstream_market_data.cli import ReplayCommandError, build_parser, positive_float, run_backtest_command, run_replay
from ledgerstream_market_data.settings import MarketDataSettings


def test_replay_parser_uses_settings_defaults() -> None:
	settings = MarketDataSettings(
		kafka_bootstrap_servers="localhost:19092",
		kafka_security_protocol=None,
		kafka_sasl_mechanism=None,
		kafka_sasl_username=None,
		kafka_sasl_password=None,
		tick_topic="market.tick",
		replay_speed=2.0,
		data_path=Path("data/sample_ticks.csv"),
		log_level="DEBUG",
		dry_run=True,
		producer_flush_timeout_seconds=5,
	)

	args = build_parser(settings).parse_args(["replay"])

	assert args.log_level == "DEBUG"
	assert args.file == "data/sample_ticks.csv"
	assert args.bootstrap_servers == "localhost:19092"
	assert args.security_protocol is None
	assert args.sasl_mechanism is None
	assert args.sasl_username is None
	assert args.sasl_password is None
	assert args.topic == "market.tick"
	assert args.speed == 2.0
	assert args.dry_run is True
	assert args.flush_timeout == 5


def test_backtest_parser_uses_strategy_defaults() -> None:
	settings = settings_with_data_path(Path("data/sample_ticks.csv"))

	args = build_parser(settings).parse_args(["backtest"])

	assert args.log_level == "INFO"
	assert args.file == "data/sample_ticks.csv"
	assert args.symbol == "AAPL"
	assert args.strategy == "moving-average-crossover"
	assert args.initial_cash == Decimal("10000.00")
	assert args.short_window == 2
	assert args.long_window == 3


def test_positive_float_rejects_zero() -> None:
	with pytest.raises(Exception, match="greater than zero"):
		positive_float("0")


def test_run_replay_rejects_missing_file(tmp_path: Path) -> None:
	settings = MarketDataSettings(
		kafka_bootstrap_servers="localhost:19092",
		kafka_security_protocol=None,
		kafka_sasl_mechanism=None,
		kafka_sasl_username=None,
		kafka_sasl_password=None,
		tick_topic="market.tick",
		replay_speed=1.0,
		data_path=tmp_path / "missing.csv",
		log_level="INFO",
		dry_run=True,
		producer_flush_timeout_seconds=10,
	)
	args = build_parser(settings).parse_args(["replay"])

	with pytest.raises(ReplayCommandError, match="replay file not found"):
		run_replay(args)


def test_run_replay_accepts_existing_file(tmp_path: Path) -> None:
	replay_file = tmp_path / "ticks.csv"
	replay_file.write_text("timestamp,symbol,bid,ask,last,volume,source\n", encoding="utf-8")
	settings = MarketDataSettings(
		kafka_bootstrap_servers="localhost:19092",
		kafka_security_protocol=None,
		kafka_sasl_mechanism=None,
		kafka_sasl_username=None,
		kafka_sasl_password=None,
		tick_topic="market.tick",
		replay_speed=1.0,
		data_path=replay_file,
		log_level="INFO",
		dry_run=True,
		producer_flush_timeout_seconds=10,
	)
	args = build_parser(settings).parse_args(["replay"])

	assert run_replay(args) == 0


def test_run_backtest_command_writes_json(capsys, tmp_path: Path) -> None:
	backtest_file = tmp_path / "ticks.csv"
	backtest_file.write_text(
		"timestamp,symbol,bid,ask,last,volume,source\n"
		"2026-01-02T14:30:00Z,AAPL,99.990000,100.010000,100.000000,1000,fixture\n"
		"2026-01-02T14:31:00Z,AAPL,109.990000,110.010000,110.000000,1000,fixture\n",
		encoding="utf-8",
	)
	args = build_parser(settings_with_data_path(backtest_file)).parse_args([
		"backtest",
		"--strategy",
		"buy-and-hold",
		"--initial-cash",
		"1000.00",
	])

	assert run_backtest_command(args) == 0
	payload = json.loads(capsys.readouterr().out)
	assert payload["strategy"] == "buy-and-hold"
	assert payload["symbol"] == "AAPL"
	assert payload["finalEquity"] == "1100.00"


def test_run_backtest_command_rejects_missing_file(tmp_path: Path) -> None:
	args = build_parser(settings_with_data_path(tmp_path / "missing.csv")).parse_args(["backtest"])

	with pytest.raises(ReplayCommandError, match="backtest file not found"):
		run_backtest_command(args)


def settings_with_data_path(data_path: Path) -> MarketDataSettings:
	return MarketDataSettings(
		kafka_bootstrap_servers="localhost:19092",
		kafka_security_protocol=None,
		kafka_sasl_mechanism=None,
		kafka_sasl_username=None,
		kafka_sasl_password=None,
		tick_topic="market.tick",
		replay_speed=1.0,
		data_path=data_path,
		log_level="INFO",
		dry_run=True,
		producer_flush_timeout_seconds=10,
	)
