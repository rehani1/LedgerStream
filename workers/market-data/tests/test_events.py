from pathlib import Path

from ledgerstream_market_data.csv_ticks import read_market_ticks
from ledgerstream_market_data.events import event_to_json, market_tick_event


def test_market_tick_event_has_stable_event_id() -> None:
	tick = next(read_market_ticks(Path("data/sample_ticks.csv")))

	first = market_tick_event(tick, sequence=1)
	second = market_tick_event(tick, sequence=1)

	assert first.eventId == second.eventId
	assert first.symbol == "AAPL"


def test_market_tick_event_serializes_as_topic_payload() -> None:
	tick = next(read_market_ticks(Path("data/sample_ticks.csv")))
	event = market_tick_event(tick, sequence=1)

	payload = event_to_json(event)

	assert '"eventId":"' in payload
	assert '"symbol":"AAPL"' in payload
	assert '"timestamp":"2026-01-02T14:30:00Z"' in payload
	assert '"bid":187.120000' in payload
	assert '"last":187.150000' in payload
	assert '"source":"fixture"' in payload
