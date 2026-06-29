# Backtesting

## Scope

LedgerStream includes a small deterministic backtesting utility in the market-data worker. It is intentionally limited to fixture data and two simple strategies:

- `buy-and-hold`
- `moving-average-crossover`

The backtester is a CLI utility, not a backend API. Keeping it in the worker avoids adding a user-facing research surface before the core paper-trading platform needs it.

## Command

Run from `workers/market-data`:

```bash
PYTHONPATH=src python -m ledgerstream_market_data backtest \
  --file data/sample_ticks.csv \
  --symbol AAPL \
  --strategy moving-average-crossover \
  --short-window 2 \
  --long-window 3 \
  --initial-cash 10000.00
```

Example output:

```json
{
  "strategy": "moving-average-crossover",
  "symbol": "AAPL",
  "startTimestamp": "2026-01-02T14:30:00Z",
  "endTimestamp": "2026-01-02T14:34:00Z",
  "ticks": 5,
  "initialCash": "10000.00",
  "finalEquity": "10019.78",
  "totalReturnPct": "0.197808",
  "maxDrawdownPct": "0.095949",
  "volatilityPct": "0.146505",
  "numberOfTrades": 1,
  "parameters": {
    "shortWindow": 2,
    "longWindow": 3,
    "positionSizing": "all-in"
  }
}
```

## Metrics

- `totalReturnPct`: `(final equity - initial cash) / initial cash * 100`.
- `maxDrawdownPct`: largest peak-to-trough drop in the simulated equity curve.
- `volatilityPct`: population standard deviation of period-to-period equity returns, expressed as a percentage. This is a lightweight approximation, not an annualized volatility estimate.
- `numberOfTrades`: count of simulated all-in buy or sell executions.

## Strategy Rules

`buy-and-hold` invests all starting cash at the first tick for the selected symbol and marks the position to market through the final tick.

`moving-average-crossover` calculates short and long simple moving averages from the selected symbol's `last` prices. It buys with all available cash when the short average crosses above the long average and sells the full position when the short average crosses below the long average. Open positions are marked to market at the final tick.

## Limitations

- Fixture data only; no live broker, live market-data provider, or historical vendor integration.
- One symbol per run.
- Long-only, all-in position sizing.
- No fees, slippage, spreads, partial fills, order-book depth, borrow costs, tax lots, dividends, or corporate actions.
- Volatility is a simple period-return standard deviation and should not be presented as an annualized risk measure.
- Results are deterministic demo analytics, not trading advice or performance claims.
