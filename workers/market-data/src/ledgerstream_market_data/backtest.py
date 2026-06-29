from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Iterable, Literal

from ledgerstream_market_data.csv_ticks import read_market_ticks

StrategyName = Literal["buy-and-hold", "moving-average-crossover"]

PERCENT_SCALE = Decimal("0.000001")
MONEY_SCALE = Decimal("0.01")
QUANTITY_SCALE = Decimal("0.00000001")
HUNDRED = Decimal("100")
ZERO = Decimal("0")


class BacktestError(Exception):
    """Raised when a deterministic backtest cannot be run."""


@dataclass(frozen=True)
class PricePoint:
    timestamp: datetime
    symbol: str
    price: Decimal


@dataclass(frozen=True)
class BacktestResult:
    strategy: StrategyName
    symbol: str
    start_timestamp: datetime
    end_timestamp: datetime
    ticks: int
    initial_cash: Decimal
    final_equity: Decimal
    total_return_pct: Decimal
    max_drawdown_pct: Decimal
    volatility_pct: Decimal
    number_of_trades: int
    parameters: dict[str, int | str]


def run_backtest(
    path: Path,
    symbol: str,
    strategy: StrategyName,
    initial_cash: Decimal = Decimal("10000.00"),
    short_window: int = 2,
    long_window: int = 3,
) -> BacktestResult:
    normalized_symbol = symbol.strip().upper()
    if not normalized_symbol:
        raise BacktestError("symbol is required")
    if initial_cash <= ZERO:
        raise BacktestError("initial cash must be greater than zero")

    points = _price_points(path, normalized_symbol)
    if not points:
        raise BacktestError(f"no fixture prices found for symbol: {normalized_symbol}")

    if strategy == "buy-and-hold":
        return _buy_and_hold(points, initial_cash)
    if strategy == "moving-average-crossover":
        return _moving_average_crossover(points, initial_cash, short_window, long_window)
    raise BacktestError(f"unsupported strategy: {strategy}")


def result_to_json(result: BacktestResult) -> str:
    payload = {
        "strategy": result.strategy,
        "symbol": result.symbol,
        "startTimestamp": _utc_timestamp(result.start_timestamp),
        "endTimestamp": _utc_timestamp(result.end_timestamp),
        "ticks": result.ticks,
        "initialCash": _decimal(result.initial_cash, MONEY_SCALE),
        "finalEquity": _decimal(result.final_equity, MONEY_SCALE),
        "totalReturnPct": _decimal(result.total_return_pct, PERCENT_SCALE),
        "maxDrawdownPct": _decimal(result.max_drawdown_pct, PERCENT_SCALE),
        "volatilityPct": _decimal(result.volatility_pct, PERCENT_SCALE),
        "numberOfTrades": result.number_of_trades,
        "parameters": result.parameters,
    }
    return json.dumps(payload, separators=(",", ":"), sort_keys=False)


def _price_points(path: Path, symbol: str) -> list[PricePoint]:
    points = [
        PricePoint(tick.timestamp, tick.symbol, tick.last)
        for tick in read_market_ticks(path)
        if tick.symbol == symbol
    ]
    return sorted(points, key=lambda point: point.timestamp)


def _buy_and_hold(points: list[PricePoint], initial_cash: Decimal) -> BacktestResult:
    quantity = initial_cash / points[0].price
    equity_curve = [quantity * point.price for point in points]
    return _result(
        strategy="buy-and-hold",
        points=points,
        initial_cash=initial_cash,
        equity_curve=equity_curve,
        number_of_trades=1,
        parameters={"positionSizing": "all-in"},
    )


def _moving_average_crossover(
    points: list[PricePoint],
    initial_cash: Decimal,
    short_window: int,
    long_window: int,
) -> BacktestResult:
    if short_window < 1:
        raise BacktestError("short window must be greater than zero")
    if long_window <= short_window:
        raise BacktestError("long window must be greater than short window")
    if len(points) < long_window:
        raise BacktestError("not enough fixture prices for requested moving average windows")

    cash = initial_cash
    quantity = ZERO
    trade_count = 0
    previous_short_ma: Decimal | None = None
    previous_long_ma: Decimal | None = None
    equity_curve: list[Decimal] = []

    for index, point in enumerate(points):
        price = point.price
        if index + 1 >= long_window:
            short_ma = _average(point.price for point in points[index + 1 - short_window:index + 1])
            long_ma = _average(point.price for point in points[index + 1 - long_window:index + 1])
            crossed_above = short_ma > long_ma and (
                previous_short_ma is None or previous_long_ma is None or previous_short_ma <= previous_long_ma
            )
            crossed_below = short_ma < long_ma and (
                previous_short_ma is None or previous_long_ma is None or previous_short_ma >= previous_long_ma
            )

            if crossed_above and cash > ZERO:
                quantity = cash / price
                cash = ZERO
                trade_count += 1
            elif crossed_below and quantity > ZERO:
                cash = quantity * price
                quantity = ZERO
                trade_count += 1

            previous_short_ma = short_ma
            previous_long_ma = long_ma

        equity_curve.append(cash + quantity * price)

    return _result(
        strategy="moving-average-crossover",
        points=points,
        initial_cash=initial_cash,
        equity_curve=equity_curve,
        number_of_trades=trade_count,
        parameters={
            "shortWindow": short_window,
            "longWindow": long_window,
            "positionSizing": "all-in",
        },
    )


def _result(
    strategy: StrategyName,
    points: list[PricePoint],
    initial_cash: Decimal,
    equity_curve: list[Decimal],
    number_of_trades: int,
    parameters: dict[str, int | str],
) -> BacktestResult:
    final_equity = equity_curve[-1]
    return BacktestResult(
        strategy=strategy,
        symbol=points[0].symbol,
        start_timestamp=points[0].timestamp,
        end_timestamp=points[-1].timestamp,
        ticks=len(points),
        initial_cash=initial_cash,
        final_equity=final_equity,
        total_return_pct=_percent_return(initial_cash, final_equity),
        max_drawdown_pct=_max_drawdown_pct(equity_curve),
        volatility_pct=_volatility_pct(equity_curve),
        number_of_trades=number_of_trades,
        parameters=parameters,
    )


def _average(values: Iterable[Decimal]) -> Decimal:
    items = list(values)
    return sum(items, ZERO) / Decimal(len(items))


def _percent_return(start: Decimal, end: Decimal) -> Decimal:
    if start == ZERO:
        return ZERO
    return (end - start) / start * HUNDRED


def _max_drawdown_pct(equity_curve: list[Decimal]) -> Decimal:
    peak = equity_curve[0]
    max_drawdown = ZERO
    for equity in equity_curve:
        if equity > peak:
            peak = equity
        if peak > ZERO:
            max_drawdown = max(max_drawdown, (peak - equity) / peak * HUNDRED)
    return max_drawdown


def _volatility_pct(equity_curve: list[Decimal]) -> Decimal:
    returns = [
        (current - previous) / previous
        for previous, current in zip(equity_curve, equity_curve[1:])
        if previous != ZERO
    ]
    if len(returns) < 2:
        return ZERO

    mean = sum(returns, ZERO) / Decimal(len(returns))
    variance = sum((value - mean) ** 2 for value in returns) / Decimal(len(returns))
    with localcontext() as context:
        context.prec = 28
        return variance.sqrt() * HUNDRED


def _decimal(value: Decimal, scale: Decimal) -> str:
    return format(value.quantize(scale), "f")


def _utc_timestamp(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
