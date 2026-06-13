import json
from datetime import datetime, timezone
from decimal import Decimal
from typing import Optional
from uuid import NAMESPACE_URL, uuid5

from pydantic import BaseModel, Field, field_serializer

from ledgerstream_market_data.models import MarketTick


class MarketTickEvent(BaseModel):
	eventId: str = Field(min_length=1)
	symbol: str = Field(min_length=1)
	timestamp: datetime
	bid: Optional[Decimal] = None
	ask: Optional[Decimal] = None
	last: Decimal
	volume: Optional[int] = None
	source: str = Field(min_length=1)

	@field_serializer("timestamp")
	def serialize_timestamp(self, value: datetime) -> str:
		return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")

	@field_serializer("bid", "ask", "last")
	def serialize_decimal(self, value: Optional[Decimal]) -> Optional[str]:
		return None if value is None else format(value, "f")


def market_tick_event(tick: MarketTick, sequence: int) -> MarketTickEvent:
	timestamp = tick.timestamp.astimezone(timezone.utc)
	event_key = "|".join(
		[
			"ledgerstream",
			"market.tick",
			str(sequence),
			tick.symbol,
			timestamp.isoformat().replace("+00:00", "Z"),
			tick.source,
		]
	)
	return MarketTickEvent(
		eventId=str(uuid5(NAMESPACE_URL, event_key)),
		symbol=tick.symbol,
		timestamp=timestamp,
		bid=tick.bid,
		ask=tick.ask,
		last=tick.last,
		volume=tick.volume,
		source=tick.source,
	)


def event_to_json(event: MarketTickEvent) -> str:
	fields = [
		("eventId", json.dumps(event.eventId)),
		("symbol", json.dumps(event.symbol)),
		("timestamp", json.dumps(event.timestamp.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"))),
		("bid", _decimal_or_null(event.bid)),
		("ask", _decimal_or_null(event.ask)),
		("last", format(event.last, "f")),
		("volume", "null" if event.volume is None else str(event.volume)),
		("source", json.dumps(event.source)),
	]
	return "{" + ",".join(f'"{name}":{value}' for name, value in fields) + "}"


def _decimal_or_null(value: Optional[Decimal]) -> str:
	return "null" if value is None else format(value, "f")
