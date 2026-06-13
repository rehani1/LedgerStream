from datetime import datetime
from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, Field, field_validator, model_validator


class MarketTick(BaseModel):
    timestamp: datetime
    symbol: str = Field(min_length=1)
    bid: Optional[Decimal] = Field(default=None, gt=0)
    ask: Optional[Decimal] = Field(default=None, gt=0)
    last: Decimal = Field(gt=0)
    volume: Optional[int] = Field(default=None, ge=0)
    source: str = Field(min_length=1)

    @field_validator("bid", "ask", "volume", mode="before")
    @classmethod
    def blank_optional_values_are_none(cls, value):
        if value == "":
            return None
        return value

    @field_validator("symbol")
    @classmethod
    def normalize_symbol(cls, value: str) -> str:
        symbol = value.strip().upper()
        if not symbol:
            raise ValueError("symbol is required")
        return symbol

    @field_validator("source")
    @classmethod
    def normalize_source(cls, value: str) -> str:
        source = value.strip()
        if not source:
            raise ValueError("source is required")
        return source

    @field_validator("timestamp")
    @classmethod
    def timestamp_requires_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("timestamp must include timezone")
        return value

    @model_validator(mode="after")
    def ask_must_not_be_below_bid(self) -> "MarketTick":
        if self.bid is not None and self.ask is not None and self.ask < self.bid:
            raise ValueError("ask must be greater than or equal to bid")
        return self
