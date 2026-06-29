import argparse
import logging
import signal
import sys
import threading
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Iterable, Optional

from ledgerstream_market_data.backtest import BacktestError, result_to_json, run_backtest
from ledgerstream_market_data.csv_ticks import CsvMarketDataError
from ledgerstream_market_data.logging_config import configure_logging
from ledgerstream_market_data.producer import ProducerError
from ledgerstream_market_data.replay import replay_market_ticks
from ledgerstream_market_data.settings import MarketDataSettings

log = logging.getLogger(__name__)


class CommandError(Exception):
	"""Raised for user-correctable command errors."""


class ReplayCommandError(CommandError):
	"""Raised for user-correctable replay command errors."""


def positive_float(value: str) -> float:
	try:
		parsed = float(value)
	except ValueError as ex:
		raise argparse.ArgumentTypeError("must be a number") from ex
	if parsed <= 0:
		raise argparse.ArgumentTypeError("must be greater than zero")
	return parsed


def positive_int(value: str) -> int:
	try:
		parsed = int(value)
	except ValueError as ex:
		raise argparse.ArgumentTypeError("must be an integer") from ex
	if parsed <= 0:
		raise argparse.ArgumentTypeError("must be greater than zero")
	return parsed


def positive_decimal(value: str) -> Decimal:
	try:
		parsed = Decimal(value)
	except InvalidOperation as ex:
		raise argparse.ArgumentTypeError("must be a decimal number") from ex
	if parsed <= Decimal("0"):
		raise argparse.ArgumentTypeError("must be greater than zero")
	return parsed


def build_parser(settings: Optional[MarketDataSettings] = None) -> argparse.ArgumentParser:
	settings = settings or MarketDataSettings.from_env()
	parser = argparse.ArgumentParser(prog="ledgerstream-market-data")
	parser.add_argument(
		"--log-level",
		default=settings.log_level,
		choices=["DEBUG", "INFO", "WARNING", "ERROR"],
		help="Worker log level.",
	)

	subparsers = parser.add_subparsers(dest="command", required=True)
	replay = subparsers.add_parser("replay", help="Replay deterministic market-data ticks.")
	replay.add_argument(
		"--file",
		default=str(settings.data_path),
		help="CSV tick file to replay.",
	)
	replay.add_argument(
		"--bootstrap-servers",
		default=settings.kafka_bootstrap_servers,
		help="Kafka-compatible bootstrap servers.",
	)
	replay.add_argument(
		"--topic",
		default=settings.tick_topic,
		help="Kafka topic for normalized market ticks.",
	)
	replay.add_argument(
		"--security-protocol",
		default=settings.kafka_security_protocol,
		help="Optional Kafka security protocol, for example SASL_SSL.",
	)
	replay.add_argument(
		"--sasl-mechanism",
		default=settings.kafka_sasl_mechanism,
		help="Optional Kafka SASL mechanism, for example SCRAM-SHA-256.",
	)
	replay.add_argument(
		"--sasl-username",
		default=settings.kafka_sasl_username,
		help="Optional Kafka SASL username.",
	)
	replay.add_argument(
		"--sasl-password",
		default=settings.kafka_sasl_password,
		help="Optional Kafka SASL password.",
	)
	replay.add_argument(
		"--speed",
		type=positive_float,
		default=settings.replay_speed,
		help="Replay speed multiplier.",
	)
	replay.add_argument(
		"--dry-run",
		action=argparse.BooleanOptionalAction,
		default=settings.dry_run,
		help="Print market.tick JSON events instead of publishing to Kafka.",
	)
	replay.add_argument(
		"--flush-timeout",
		type=positive_float,
		default=settings.producer_flush_timeout_seconds,
		help="Kafka producer flush timeout in seconds.",
	)
	replay.set_defaults(handler=run_replay)

	backtest = subparsers.add_parser("backtest", help="Run deterministic fixture-based backtests.")
	backtest.add_argument(
		"--file",
		default=str(settings.data_path),
		help="CSV tick file to backtest.",
	)
	backtest.add_argument(
		"--symbol",
		default="AAPL",
		help="Symbol to backtest from the fixture.",
	)
	backtest.add_argument(
		"--strategy",
		choices=["buy-and-hold", "moving-average-crossover"],
		default="moving-average-crossover",
		help="Simple backtest strategy.",
	)
	backtest.add_argument(
		"--initial-cash",
		type=positive_decimal,
		default=Decimal("10000.00"),
		help="Starting cash for the deterministic simulation.",
	)
	backtest.add_argument(
		"--short-window",
		type=positive_int,
		default=2,
		help="Short moving-average window for moving-average-crossover.",
	)
	backtest.add_argument(
		"--long-window",
		type=positive_int,
		default=3,
		help="Long moving-average window for moving-average-crossover.",
	)
	backtest.set_defaults(handler=run_backtest_command)
	return parser


def main(argv: Optional[Iterable[str]] = None) -> int:
	parser = build_parser()
	args = parser.parse_args(list(argv) if argv is not None else None)
	configure_logging(args.log_level)
	shutdown_event = threading.Event()
	install_signal_handlers(shutdown_event)
	args.shutdown_event = shutdown_event
	try:
		return args.handler(args)
	except CommandError as ex:
		print(f"error: {ex}", file=sys.stderr)
		return 2


def run_replay(args: argparse.Namespace) -> int:
	replay_file = Path(args.file)
	if not replay_file.exists():
		raise ReplayCommandError(f"replay file not found: {replay_file}")
	if not replay_file.is_file():
		raise ReplayCommandError(f"replay path is not a file: {replay_file}")

	try:
		tick_count = replay_market_ticks(
			replay_file,
			topic=args.topic,
			bootstrap_servers=args.bootstrap_servers,
			speed=args.speed,
			kafka_security_protocol=args.security_protocol,
			kafka_sasl_mechanism=args.sasl_mechanism,
			kafka_sasl_username=args.sasl_username,
			kafka_sasl_password=args.sasl_password,
			dry_run=args.dry_run,
			shutdown_event=getattr(args, "shutdown_event", None),
			flush_timeout_seconds=args.flush_timeout,
		)
	except (CsvMarketDataError, ProducerError) as ex:
		raise ReplayCommandError(str(ex)) from ex

	log.info(
		"Market replay completed",
		extra={
			"replay_file": str(replay_file),
			"bootstrap_servers": args.bootstrap_servers,
			"topic": args.topic,
			"speed": args.speed,
			"dry_run": args.dry_run,
			"tick_count": tick_count,
		},
	)
	return 0


def run_backtest_command(args: argparse.Namespace) -> int:
	backtest_file = Path(args.file)
	if not backtest_file.exists():
		raise ReplayCommandError(f"backtest file not found: {backtest_file}")
	if not backtest_file.is_file():
		raise ReplayCommandError(f"backtest path is not a file: {backtest_file}")

	try:
		result = run_backtest(
			backtest_file,
			symbol=args.symbol,
			strategy=args.strategy,
			initial_cash=args.initial_cash,
			short_window=args.short_window,
			long_window=args.long_window,
		)
	except (BacktestError, CsvMarketDataError) as ex:
		raise ReplayCommandError(str(ex)) from ex

	print(result_to_json(result))
	log.info(
		"Backtest completed",
		extra={
			"backtest_file": str(backtest_file),
			"symbol": result.symbol,
			"strategy": result.strategy,
			"ticks": result.ticks,
			"number_of_trades": result.number_of_trades,
		},
	)
	return 0


def install_signal_handlers(shutdown_event: threading.Event) -> None:
	def request_shutdown(signum, _frame) -> None:
		log.info("Shutdown requested", extra={"signal": signum})
		shutdown_event.set()

	signal.signal(signal.SIGINT, request_shutdown)
	signal.signal(signal.SIGTERM, request_shutdown)
