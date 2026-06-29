import json
from decimal import Decimal
from pathlib import Path

import pytest

from ledgerstream_market_data.backtest import BacktestError, result_to_json, run_backtest


def test_buy_and_hold_backtest_returns_deterministic_metrics(tmp_path: Path) -> None:
	csv_file = write_prices(tmp_path, [Decimal("100.000000"), Decimal("110.000000"), Decimal("121.000000")])

	result = run_backtest(
		csv_file,
		symbol="aapl",
		strategy="buy-and-hold",
		initial_cash=Decimal("1000.00"),
	)
	payload = json.loads(result_to_json(result))

	assert payload["strategy"] == "buy-and-hold"
	assert payload["symbol"] == "AAPL"
	assert payload["ticks"] == 3
	assert payload["initialCash"] == "1000.00"
	assert payload["finalEquity"] == "1210.00"
	assert payload["totalReturnPct"] == "21.000000"
	assert payload["maxDrawdownPct"] == "0.000000"
	assert payload["volatilityPct"] == "0.000000"
	assert payload["numberOfTrades"] == 1


def test_moving_average_crossover_records_trade_count_and_drawdown(tmp_path: Path) -> None:
	csv_file = write_prices(
		tmp_path,
		[
			Decimal("100.000000"),
			Decimal("101.000000"),
			Decimal("102.000000"),
			Decimal("99.000000"),
			Decimal("98.000000"),
			Decimal("103.000000"),
		],
	)

	result = run_backtest(
		csv_file,
		symbol="AAPL",
		strategy="moving-average-crossover",
		initial_cash=Decimal("1000.00"),
		short_window=2,
		long_window=3,
	)
	payload = json.loads(result_to_json(result))

	assert payload["strategy"] == "moving-average-crossover"
	assert payload["finalEquity"] == "970.59"
	assert payload["totalReturnPct"] == "-2.941176"
	assert payload["maxDrawdownPct"] == "2.941176"
	assert payload["numberOfTrades"] == 3
	assert payload["parameters"] == {
		"shortWindow": 2,
		"longWindow": 3,
		"positionSizing": "all-in",
	}


def test_backtest_rejects_missing_symbol(tmp_path: Path) -> None:
	csv_file = write_prices(tmp_path, [Decimal("100.000000")])

	with pytest.raises(BacktestError, match="no fixture prices found for symbol: MSFT"):
		run_backtest(csv_file, symbol="MSFT", strategy="buy-and-hold")


def test_moving_average_crossover_rejects_invalid_windows(tmp_path: Path) -> None:
	csv_file = write_prices(tmp_path, [Decimal("100.000000"), Decimal("101.000000"), Decimal("102.000000")])

	with pytest.raises(BacktestError, match="long window must be greater than short window"):
		run_backtest(
			csv_file,
			symbol="AAPL",
			strategy="moving-average-crossover",
			short_window=3,
			long_window=3,
		)


def write_prices(tmp_path: Path, prices: list[Decimal]) -> Path:
	csv_file = tmp_path / "ticks.csv"
	lines = ["timestamp,symbol,bid,ask,last,volume,source"]
	for index, price in enumerate(prices):
		lines.append(
			f"2026-01-02T14:{30 + index:02d}:00Z,"
			f"AAPL,{price - Decimal('0.010000')},{price + Decimal('0.010000')},{price},1000,fixture"
		)
	csv_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
	return csv_file
