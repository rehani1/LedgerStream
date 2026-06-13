import { useEffect, useMemo, useState } from 'react';
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis, CartesianGrid } from 'recharts';
import { useQuery } from '@tanstack/react-query';

import { consumeQuoteStream, getLatestQuote, getQuoteHistory, listSymbols } from '../api/quotes';
import { useAuth } from '../auth/AuthContext';
import type { Quote } from '../api/types';

type StreamStatus = 'idle' | 'connecting' | 'live' | 'closed' | 'error';

export function DashboardPage() {
  const auth = useAuth();
  const [selectedTicker, setSelectedTicker] = useState<string | null>(null);
  const [liveQuotes, setLiveQuotes] = useState<Record<string, Quote>>({});
  const [streamStatus, setStreamStatus] = useState<StreamStatus>('idle');
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);

  const symbolsQuery = useQuery({
    queryKey: ['symbols'],
    queryFn: () => listSymbols(auth.accessToken ?? ''),
    enabled: auth.status === 'authenticated' && Boolean(auth.accessToken)
  });
  const symbols = symbolsQuery.data ?? [];
  const tickers = useMemo(() => symbols.map((symbol) => symbol.ticker), [symbols]);
  const selectedSymbol = selectedTicker ?? tickers[0] ?? null;
  const selectedQuote = selectedSymbol ? liveQuotes[selectedSymbol] : null;

  const latestQuotesQuery = useQuery({
    queryKey: ['latest-quotes', tickers],
    queryFn: async () => {
      const quotes = await Promise.allSettled(
        tickers.map((ticker) => getLatestQuote(ticker, auth.accessToken ?? ''))
      );

      return quotes
        .filter((result): result is PromiseFulfilledResult<Quote> => result.status === 'fulfilled')
        .map((result) => result.value);
    },
    enabled: auth.status === 'authenticated' && Boolean(auth.accessToken) && tickers.length > 0
  });

  const historyQuery = useQuery({
    queryKey: ['quote-history', selectedSymbol],
    queryFn: () => getQuoteHistory(selectedSymbol ?? '', auth.accessToken ?? '', '1d', 100),
    enabled: auth.status === 'authenticated' && Boolean(auth.accessToken) && Boolean(selectedSymbol)
  });

  useEffect(() => {
    if (!selectedTicker && tickers.length > 0) {
      setSelectedTicker(tickers[0]);
    }
  }, [selectedTicker, tickers]);

  useEffect(() => {
    if (!latestQuotesQuery.data) {
      return;
    }

    setLiveQuotes((current) => {
      const next = { ...current };

      for (const quote of latestQuotesQuery.data) {
        if (next[quote.symbol] && next[quote.symbol].timestamp > quote.timestamp) {
          continue;
        }

        next[quote.symbol] = quote;
      }

      return next;
    });
  }, [latestQuotesQuery.data]);

  useEffect(() => {
    if (!auth.accessToken || tickers.length === 0) {
      setStreamStatus('idle');
      return;
    }

    const controller = new AbortController();
    setStreamStatus('connecting');

    consumeQuoteStream({
      symbols: tickers,
      accessToken: auth.accessToken,
      signal: controller.signal,
      onReady: () => setStreamStatus('live'),
      onQuote: (quote) => {
        setStreamStatus('live');
        setLastUpdatedAt(quote.timestamp);
        setLiveQuotes((current) => ({
          ...current,
          [quote.symbol]: quote
        }));
      }
    })
      .then(() => {
        if (!controller.signal.aborted) {
          setStreamStatus('closed');
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setStreamStatus('error');
        }
      });

    return () => {
      controller.abort();
    };
  }, [auth.accessToken, tickers]);

  const chartData = useMemo(() => {
    const byTimestamp = new Map<string, Quote>();

    for (const quote of historyQuery.data?.ticks ?? []) {
      byTimestamp.set(quote.timestamp, quote);
    }

    if (selectedQuote) {
      byTimestamp.set(selectedQuote.timestamp, selectedQuote);
    }

    return Array.from(byTimestamp.values())
      .sort((left, right) => left.timestamp.localeCompare(right.timestamp))
      .map((quote) => ({
        time: formatTime(quote.timestamp),
        price: Number(quote.last)
      }));
  }, [historyQuery.data?.ticks, selectedQuote]);

  return (
    <section className="page-stack" aria-labelledby="dashboard-title">
      <div className="page-heading dashboard-heading">
        <div>
          <p className="eyebrow">Markets</p>
          <h1 id="dashboard-title">Quote Dashboard</h1>
        </div>
        <div className={`status-pill status-${streamStatus}`}>
          <span aria-hidden="true" />
          {streamLabel(streamStatus)}
        </div>
      </div>
      <div className="metric-grid" aria-label="Market summary">
        <article className="metric-card">
          <span className="metric-label">Symbols</span>
          <strong>{symbols.length || '—'}</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Selected</span>
          <strong>{selectedSymbol ?? '—'}</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Last price</span>
          <strong>{selectedQuote ? formatPrice(selectedQuote.last) : '—'}</strong>
        </article>
      </div>
      <div className="dashboard-grid">
        <div className="table-panel">
          <div className="table-heading">
            <h2>Live Quotes</h2>
            <span>{lastUpdatedAt ? `Updated ${formatTime(lastUpdatedAt)}` : quoteLoadState(symbolsQuery.isFetching, latestQuotesQuery.isFetching)}</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>Ticker</th>
                <th>Name</th>
                <th>Bid</th>
                <th>Ask</th>
                <th>Last</th>
                <th>Volume</th>
              </tr>
            </thead>
            <tbody>
              {symbols.map((symbol) => {
                const quote = liveQuotes[symbol.ticker];
                const selected = selectedSymbol === symbol.ticker;

                return (
                  <tr key={symbol.ticker} className={selected ? 'selected-row' : undefined}>
                    <td>
                      <button
                        type="button"
                        className="ticker-button"
                        onClick={() => setSelectedTicker(symbol.ticker)}
                        aria-pressed={selected}
                      >
                        {symbol.ticker}
                      </button>
                    </td>
                    <td>{symbol.name}</td>
                    <td>{quote?.bid != null ? formatPrice(quote.bid) : '—'}</td>
                    <td>{quote?.ask != null ? formatPrice(quote.ask) : '—'}</td>
                    <td>{quote ? formatPrice(quote.last) : '—'}</td>
                    <td>{quote?.volume != null ? formatVolume(quote.volume) : '—'}</td>
                  </tr>
                );
              })}
              {!symbols.length && (
                <tr>
                  <td colSpan={6}>No symbols loaded</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="chart-panel">
          <div className="table-heading">
            <h2>{selectedSymbol ? `${selectedSymbol} Price` : 'Price'}</h2>
            <span>{historyQuery.isFetching ? 'Loading' : `${chartData.length} points`}</span>
          </div>
          <div className="chart-frame">
            {chartData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%" minWidth={320} minHeight={240}>
                <LineChart data={chartData} margin={{ top: 12, right: 20, bottom: 6, left: 0 }}>
                  <CartesianGrid stroke="#edf0eb" vertical={false} />
                  <XAxis dataKey="time" tickLine={false} axisLine={false} minTickGap={24} />
                  <YAxis
                    domain={['dataMin', 'dataMax']}
                    tickFormatter={(value) => formatPrice(Number(value))}
                    tickLine={false}
                    axisLine={false}
                    width={72}
                  />
                  <Tooltip formatter={(value) => [formatPrice(Number(value)), 'Last']} />
                  <Line type="monotone" dataKey="price" stroke="#116149" strokeWidth={2.5} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="chart-empty">No price history</div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function streamLabel(status: StreamStatus) {
  const labels: Record<StreamStatus, string> = {
    idle: 'Idle',
    connecting: 'Connecting',
    live: 'Live',
    closed: 'Closed',
    error: 'Error'
  };

  return labels[status];
}

function quoteLoadState(symbolsLoading: boolean, quotesLoading: boolean) {
  if (symbolsLoading || quotesLoading) {
    return 'Loading';
  }

  return 'Ready';
}

function formatPrice(value: number) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
}

function formatVolume(value: number) {
  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 1
  }).format(value);
}

function formatTime(timestamp: string) {
  return new Intl.DateTimeFormat('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date(timestamp));
}
