import csv
from pathlib import Path
from typing import Iterator

from pydantic import ValidationError

from ledgerstream_market_data.models import MarketTick

CSV_COLUMNS = ["timestamp", "symbol", "bid", "ask", "last", "volume", "source"]


class CsvMarketDataError(Exception):
    """Raised when a market-data CSV cannot be parsed into valid ticks."""


def read_market_ticks(path: Path) -> Iterator[MarketTick]:
    with path.open(newline="", encoding="utf-8") as csv_file:
        reader = csv.DictReader(csv_file)
        validate_columns(path, reader.fieldnames)
        for line_number, row in enumerate(reader, start=2):
            try:
                yield MarketTick.model_validate(row)
            except ValidationError as ex:
                raise CsvMarketDataError(f"{path}:{line_number}: invalid market tick: {ex}") from ex


def validate_columns(path: Path, fieldnames) -> None:
    if fieldnames is None:
        raise CsvMarketDataError(f"{path}: CSV header is required")
    missing = [column for column in CSV_COLUMNS if column not in fieldnames]
    if missing:
        raise CsvMarketDataError(f"{path}: missing required columns: {', '.join(missing)}")
