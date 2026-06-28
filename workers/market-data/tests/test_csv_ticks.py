from datetime import timezone
from decimal import Decimal
from pathlib import Path

import pytest

from ledgerstream_market_data.csv_ticks import CsvMarketDataError, read_market_ticks


def test_read_market_ticks_parses_valid_rows(tmp_path: Path) -> None:
    csv_file = tmp_path / "ticks.csv"
    csv_file.write_text(
        "timestamp,symbol,bid,ask,last,volume,source\n"
        "2026-01-02T14:30:00Z,aapl,187.120000,187.180000,187.150000,125000,fixture\n",
        encoding="utf-8",
    )

    ticks = list(read_market_ticks(csv_file))

    assert len(ticks) == 1
    assert ticks[0].symbol == "AAPL"
    assert ticks[0].timestamp.tzinfo == timezone.utc
    assert ticks[0].last == Decimal("187.150000")
    assert ticks[0].volume == 125000


def test_read_market_ticks_rejects_missing_columns(tmp_path: Path) -> None:
    csv_file = tmp_path / "ticks.csv"
    csv_file.write_text("timestamp,symbol,last\n2026-01-02T14:30:00Z,AAPL,187.150000\n", encoding="utf-8")

    with pytest.raises(CsvMarketDataError, match="missing required columns"):
        list(read_market_ticks(csv_file))


def test_read_market_ticks_rejects_invalid_prices(tmp_path: Path) -> None:
    csv_file = tmp_path / "ticks.csv"
    csv_file.write_text(
        "timestamp,symbol,bid,ask,last,volume,source\n"
        "2026-01-02T14:30:00Z,AAPL,187.200000,187.100000,187.150000,125000,fixture\n",
        encoding="utf-8",
    )

    with pytest.raises(CsvMarketDataError, match="ask must be greater than or equal to bid"):
        list(read_market_ticks(csv_file))


def test_read_market_ticks_reports_invalid_row_context(tmp_path: Path) -> None:
    csv_file = tmp_path / "ticks.csv"
    csv_file.write_text(
        "timestamp,symbol,bid,ask,last,volume,source\n"
        "2026-01-02T14:30:00,AAPL,187.120000,187.180000,187.150000,125000,fixture\n",
        encoding="utf-8",
    )

    with pytest.raises(CsvMarketDataError) as exc_info:
        list(read_market_ticks(csv_file))

    message = str(exc_info.value)
    assert ":2: invalid market tick:" in message
    assert "timestamp must include timezone" in message


def test_sample_fixture_contains_supported_symbols() -> None:
    fixture = Path("data/sample_ticks.csv")
    ticks = list(read_market_ticks(fixture))

    assert len(ticks) == 25
    assert {tick.symbol for tick in ticks} == {"AAPL", "MSFT", "NVDA", "SPY", "TSLA"}
    assert {tick.source for tick in ticks} == {"fixture"}
