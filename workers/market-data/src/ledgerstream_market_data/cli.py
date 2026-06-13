import argparse
import logging
import sys
from pathlib import Path
from typing import Iterable, Optional

from ledgerstream_market_data.logging_config import configure_logging
from ledgerstream_market_data.settings import MarketDataSettings

log = logging.getLogger(__name__)


class ReplayCommandError(Exception):
	"""Raised for user-correctable replay command errors."""


def positive_float(value: str) -> float:
	try:
		parsed = float(value)
	except ValueError as ex:
		raise argparse.ArgumentTypeError("must be a number") from ex
	if parsed <= 0:
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
		"--speed",
		type=positive_float,
		default=settings.replay_speed,
		help="Replay speed multiplier.",
	)
	replay.set_defaults(handler=run_replay)
	return parser


def main(argv: Optional[Iterable[str]] = None) -> int:
	parser = build_parser()
	args = parser.parse_args(list(argv) if argv is not None else None)
	configure_logging(args.log_level)
	try:
		return args.handler(args)
	except ReplayCommandError as ex:
		print(f"error: {ex}", file=sys.stderr)
		return 2


def run_replay(args: argparse.Namespace) -> int:
	replay_file = Path(args.file)
	if not replay_file.exists():
		raise ReplayCommandError(f"replay file not found: {replay_file}")
	if not replay_file.is_file():
		raise ReplayCommandError(f"replay path is not a file: {replay_file}")

	log.info(
		"Market replay configuration validated",
		extra={
			"replay_file": str(replay_file),
			"bootstrap_servers": args.bootstrap_servers,
			"topic": args.topic,
			"speed": args.speed,
		},
	)
	return 0
