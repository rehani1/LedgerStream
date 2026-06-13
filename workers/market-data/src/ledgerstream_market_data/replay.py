import sys
import time
from pathlib import Path
from threading import Event
from typing import Callable, Optional, TextIO

from ledgerstream_market_data.csv_ticks import read_market_ticks
from ledgerstream_market_data.events import event_to_json, market_tick_event
from ledgerstream_market_data.producer import KafkaMarketTickPublisher


Sleeper = Callable[[float], None]


def replay_market_ticks(
	path: Path,
	topic: str,
	bootstrap_servers: str,
	speed: float,
	dry_run: bool = False,
	output: TextIO = sys.stdout,
	sleeper: Sleeper = time.sleep,
	shutdown_event: Optional[Event] = None,
	flush_timeout_seconds: float = 10,
) -> int:
	publisher = None if dry_run else KafkaMarketTickPublisher(bootstrap_servers)
	published_count = 0
	previous_timestamp = None

	for sequence, tick in enumerate(read_market_ticks(path), start=1):
		if shutdown_event is not None and shutdown_event.is_set():
			break

		if previous_timestamp is not None and not dry_run:
			delay_seconds = max(0.0, (tick.timestamp - previous_timestamp).total_seconds() / speed)
			if delay_seconds > 0 and _shutdown_requested(delay_seconds, shutdown_event, sleeper):
				break

		event = market_tick_event(tick, sequence)
		payload = event_to_json(event)
		if dry_run:
			print(payload, file=output)
		else:
			publisher.publish(topic, event.symbol, payload)

		published_count += 1
		previous_timestamp = tick.timestamp

	if publisher is not None:
		publisher.flush(flush_timeout_seconds)
	return published_count


def _shutdown_requested(delay_seconds: float, shutdown_event: Optional[Event], sleeper: Sleeper) -> bool:
	if shutdown_event is not None:
		return shutdown_event.wait(delay_seconds)
	sleeper(delay_seconds)
	return False
