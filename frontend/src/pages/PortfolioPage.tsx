import { useMemo, useState, type FormEvent } from 'react';
import { ArrowDownToLine, ArrowUpFromLine } from 'lucide-react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  depositCash,
  getPortfolioHistory,
  getPortfolioSummary,
  listLedgerEntries,
  listPortfolioPositions,
  withdrawCash
} from '../api/portfolio';
import type { CashTransferType } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const ledgerPageSize = 10;
const cashTransferTypes: CashTransferType[] = ['DEPOSIT', 'WITHDRAWAL'];

export function PortfolioPage() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const [ledgerPage, setLedgerPage] = useState(0);
  const [cashTransferType, setCashTransferType] = useState<CashTransferType>('DEPOSIT');
  const [cashAmount, setCashAmount] = useState('25000.00');
  const [cashNote, setCashNote] = useState('Demo paper funding');
  const [cashFormError, setCashFormError] = useState<string | null>(null);
  const [cashResultMessage, setCashResultMessage] = useState<string | null>(null);
  const accessToken = auth.accessToken ?? '';
  const userId = auth.user?.id ?? 'anonymous';
  const queriesEnabled = auth.status === 'authenticated' && Boolean(auth.accessToken);

  const summaryQuery = useQuery({
    queryKey: ['portfolio-summary', userId],
    queryFn: () => getPortfolioSummary(accessToken),
    enabled: queriesEnabled
  });

  const positionsQuery = useQuery({
    queryKey: ['portfolio-positions', userId],
    queryFn: () => listPortfolioPositions(accessToken),
    enabled: queriesEnabled
  });

  const ledgerQuery = useQuery({
    queryKey: ['portfolio-ledger', userId, ledgerPage],
    queryFn: () => listLedgerEntries(accessToken, ledgerPage, ledgerPageSize),
    enabled: queriesEnabled,
    placeholderData: (previousData) => previousData
  });

  const historyQuery = useQuery({
    queryKey: ['portfolio-history', userId],
    queryFn: () => getPortfolioHistory(accessToken, 0, 50),
    enabled: queriesEnabled
  });

  function invalidatePortfolioState() {
    setLedgerPage(0);
    queryClient.invalidateQueries({ queryKey: ['portfolio-summary', userId] });
    queryClient.invalidateQueries({ queryKey: ['portfolio-positions', userId] });
    queryClient.invalidateQueries({ queryKey: ['portfolio-ledger', userId] });
    queryClient.invalidateQueries({ queryKey: ['portfolio-history', userId] });
    queryClient.invalidateQueries({ queryKey: ['risk-latest', userId] });
    queryClient.invalidateQueries({ queryKey: ['risk-history', userId] });
  }

  const cashTransferMutation = useMutation({
    mutationFn: (payload: { transferType: CashTransferType; amount: number; note: string | null; idempotencyKey: string }) => {
      const request = { amount: payload.amount, note: payload.note };
      if (payload.transferType === 'DEPOSIT') {
        return depositCash(request, accessToken, payload.idempotencyKey);
      }
      return withdrawCash(request, accessToken, payload.idempotencyKey);
    },
    onSuccess: (response) => {
      setCashFormError(null);
      setCashResultMessage(
        `${formatCashTransferType(response.transferType)} recorded. Cash balance ${formatMoney(
          response.portfolio.cash,
          response.portfolio.baseCurrency
        )}.`
      );
      setCashAmount('');
      setCashNote('');
      invalidatePortfolioState();
    }
  });

  function handleCashSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const parsedAmount = parseCashAmount(cashAmount);
    if (typeof parsedAmount === 'string') {
      setCashFormError(parsedAmount);
      return;
    }
    if (cashTransferType === 'WITHDRAWAL' && summary && parsedAmount > summary.cash) {
      setCashFormError(`Cash available is ${formatMoney(summary.cash, summary.baseCurrency)}.`);
      return;
    }

    setCashFormError(null);
    setCashResultMessage(null);
    cashTransferMutation.mutate({
      transferType: cashTransferType,
      amount: parsedAmount,
      note: cashNote.trim() === '' ? null : cashNote.trim(),
      idempotencyKey: makeCashIdempotencyKey(cashTransferType)
    });
  }

  const summary = summaryQuery.data;
  const positions = positionsQuery.data ?? [];
  const ledger = ledgerQuery.data;
  const history = historyQuery.data?.snapshots ?? [];
  const chartData = useMemo(() => {
    return [...history]
      .sort((left, right) => left.createdAt.localeCompare(right.createdAt))
      .map((snapshot) => ({
        time: formatTime(snapshot.createdAt),
        totalEquity: snapshot.totalEquity,
        cash: snapshot.cash,
        unrealizedPnl: snapshot.unrealizedPnl
      }));
  }, [history]);
  const totalLedgerPages = Math.max(ledger?.totalPages ?? 0, 1);
  const pageLabel = `Page ${ledgerPage + 1} of ${totalLedgerPages}`;
  const canGoBack = ledgerPage > 0;
  const canGoForward = ledger ? ledgerPage + 1 < ledger.totalPages : false;
  const errorMessage = firstErrorMessage(summaryQuery.error, positionsQuery.error, ledgerQuery.error, historyQuery.error);

  return (
    <section className="page-stack" aria-labelledby="portfolio-title">
      <div className="page-heading dashboard-heading">
        <div>
          <p className="eyebrow">Portfolio</p>
          <h1 id="portfolio-title">Positions</h1>
        </div>
        <span className="summary-timestamp">
          {summary ? `Updated ${formatDateTime(summary.updatedAt)}` : 'Loading'}
        </span>
      </div>

      {errorMessage && (
        <div className="empty-panel error-panel" role="alert">
          <strong>Portfolio data unavailable</strong>
          <span>{errorMessage}</span>
        </div>
      )}

      <div className="metric-grid portfolio-summary-grid" aria-label="Portfolio summary">
        <article className="metric-card">
          <span className="metric-label">Cash</span>
          <strong>{summaryQuery.isLoading ? 'Loading' : formatMoney(summary?.cash, summary?.baseCurrency)}</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Total equity</span>
          <strong>{summaryQuery.isLoading ? 'Loading' : formatMoney(summary?.totalEquity, summary?.baseCurrency)}</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Realized P&amp;L</span>
          <strong className={valueTone(summary?.realizedPnl)}>
            {summaryQuery.isLoading ? 'Loading' : formatMoney(summary?.realizedPnl, summary?.baseCurrency)}
          </strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Unrealized P&amp;L</span>
          <strong className={valueTone(summary?.unrealizedPnl)}>
            {summaryQuery.isLoading ? 'Loading' : formatMoney(summary?.unrealizedPnl, summary?.baseCurrency)}
          </strong>
        </article>
      </div>

      <div className="portfolio-detail-grid" aria-label="Portfolio detail">
        <article className="detail-tile">
          <span className="metric-label">Market value</span>
          <strong>{formatMoney(summary?.marketValue, summary?.baseCurrency)}</strong>
        </article>
        <article className="detail-tile">
          <span className="metric-label">Positions priced</span>
          <strong>{summary ? `${summary.pricedPositionsCount}/${summary.positionsCount}` : '—'}</strong>
        </article>
      </div>

      <div className="cash-workspace" aria-label="Paper cash controls">
        <form className="form-panel cash-transfer-panel" onSubmit={handleCashSubmit} aria-label="Paper cash" noValidate>
          <div className="table-heading compact-heading">
            <h2>Paper Cash</h2>
            <span>{formatCashTransferType(cashTransferType)}</span>
          </div>

          <fieldset className="segmented-field">
            <legend>Action</legend>
            <div className="segmented-control">
              {cashTransferTypes.map((availableType) => (
                <button
                  key={availableType}
                  type="button"
                  className={cashTransferType === availableType ? 'active' : undefined}
                  aria-pressed={cashTransferType === availableType}
                  onClick={() => setCashTransferType(availableType)}
                  disabled={cashTransferMutation.isPending}
                >
                  {formatCashTransferType(availableType)}
                </button>
              ))}
            </div>
          </fieldset>

          <label>
            Amount
            <input
              type="number"
              min="0.01"
              max="1000000"
              step="0.01"
              inputMode="decimal"
              value={cashAmount}
              onChange={(event) => setCashAmount(event.target.value)}
              disabled={cashTransferMutation.isPending}
            />
          </label>

          <label>
            Note
            <input
              type="text"
              maxLength={120}
              value={cashNote}
              onChange={(event) => setCashNote(event.target.value)}
              disabled={cashTransferMutation.isPending}
            />
          </label>

          <button type="submit" className="primary-button icon-submit-button" disabled={cashTransferMutation.isPending}>
            {cashTransferType === 'DEPOSIT' ? (
              <ArrowDownToLine aria-hidden="true" size={18} />
            ) : (
              <ArrowUpFromLine aria-hidden="true" size={18} />
            )}
            <span>
              {cashTransferMutation.isPending
                ? 'Recording'
                : `${formatCashTransferType(cashTransferType)} cash`}
            </span>
          </button>

          {cashFormError && <p className="form-error" role="alert">{cashFormError}</p>}
          {cashTransferMutation.error instanceof Error && (
            <p className="form-error" role="alert">{cashTransferMutation.error.message}</p>
          )}
          {cashResultMessage && (
            <div className="order-result" aria-live="polite">
              <strong>{cashResultMessage}</strong>
              <small>{formatDateTime(new Date().toISOString())}</small>
            </div>
          )}
        </form>
      </div>

      <div className="chart-panel">
        <div className="table-heading">
          <h2>Equity History</h2>
          <span>{historyQuery.isFetching ? 'Loading' : `${chartData.length} points`}</span>
        </div>
        <div className="chart-frame">
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height="100%" minWidth={320} minHeight={240}>
              <LineChart data={chartData} margin={{ top: 12, right: 20, bottom: 6, left: 0 }}>
                <CartesianGrid stroke="#edf0eb" vertical={false} />
                <XAxis dataKey="time" tickLine={false} axisLine={false} minTickGap={24} />
                <YAxis
                  tickFormatter={(value) => formatCompactMoney(Number(value), summary?.baseCurrency)}
                  tickLine={false}
                  axisLine={false}
                  width={72}
                />
                <Tooltip
                  formatter={(value, name) => [
                    formatMoney(Number(value), summary?.baseCurrency),
                    portfolioMetricLabel(String(name))
                  ]}
                />
                <Line type="monotone" dataKey="totalEquity" stroke="#116149" strokeWidth={2.5} dot={false} />
                <Line type="monotone" dataKey="cash" stroke="#6f4635" strokeWidth={2.2} dot={false} />
                <Line type="monotone" dataKey="unrealizedPnl" stroke="#315d8a" strokeWidth={2.2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="chart-empty">No portfolio history</div>
          )}
        </div>
      </div>

      <div className="table-panel">
        <div className="table-heading">
          <h2>Positions</h2>
          <span>{positionsQuery.isFetching ? 'Loading' : `${positions.length} open`}</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Quantity</th>
              <th>Avg cost</th>
              <th>Last</th>
              <th>Market value</th>
              <th>Unrealized</th>
              <th>Realized</th>
            </tr>
          </thead>
          <tbody>
            {positionsQuery.isLoading && (
              <tr>
                <td colSpan={7}>Loading positions</td>
              </tr>
            )}
            {!positionsQuery.isLoading && positions.length === 0 && (
              <tr>
                <td colSpan={7}>No open positions</td>
              </tr>
            )}
            {positions.map((position) => (
              <tr key={position.id}>
                <td>
                  <strong>{position.symbol}</strong>
                </td>
                <td>{formatQuantity(position.quantity)}</td>
                <td>{formatMoney(position.avgCost, summary?.baseCurrency)}</td>
                <td>{formatMoney(position.lastPrice, summary?.baseCurrency)}</td>
                <td>{formatMoney(position.marketValue, summary?.baseCurrency)}</td>
                <td className={valueTone(position.unrealizedPnl)}>
                  {formatMoney(position.unrealizedPnl, summary?.baseCurrency)}
                </td>
                <td className={valueTone(position.realizedPnl)}>
                  {formatMoney(position.realizedPnl, summary?.baseCurrency)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="table-panel">
        <div className="table-heading">
          <h2>Ledger</h2>
          <span>{ledgerQuery.isFetching ? 'Loading' : `${ledger?.totalElements ?? 0} entries`}</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Type</th>
              <th>Cash</th>
              <th>Symbol</th>
              <th>Quantity</th>
              <th>Price</th>
            </tr>
          </thead>
          <tbody>
            {ledgerQuery.isLoading && (
              <tr>
                <td colSpan={6}>Loading ledger</td>
              </tr>
            )}
            {!ledgerQuery.isLoading && (ledger?.entries.length ?? 0) === 0 && (
              <tr>
                <td colSpan={6}>No ledger entries</td>
              </tr>
            )}
            {ledger?.entries.map((entry) => (
              <tr key={entry.id}>
                <td>{formatDateTime(entry.createdAt)}</td>
                <td>{formatEntryType(entry.entryType)}</td>
                <td className={valueTone(entry.cashDelta)}>
                  {formatMoney(entry.cashDelta, summary?.baseCurrency)}
                </td>
                <td>{entry.symbol ?? '—'}</td>
                <td>{formatQuantity(entry.quantityDelta)}</td>
                <td>{formatMoney(entry.price, summary?.baseCurrency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="table-footer">
          <span>{pageLabel}</span>
          <div className="pagination-controls" aria-label="Ledger pagination">
            <button
              type="button"
              className="secondary-button"
              onClick={() => setLedgerPage((page) => Math.max(0, page - 1))}
              disabled={!canGoBack}
            >
              Previous
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setLedgerPage((page) => page + 1)}
              disabled={!canGoForward}
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}

function firstErrorMessage(...errors: unknown[]) {
  const error = errors.find(Boolean);

  if (!error) {
    return null;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'Refresh the page or try again later.';
}

function formatMoney(value: number | null | undefined, currency = 'USD') {
  if (value == null) {
    return '—';
  }

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
}

function formatCompactMoney(value: number, currency = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    notation: 'compact',
    maximumFractionDigits: 1
  }).format(value);
}

function formatQuantity(value: number | null | undefined) {
  if (value == null) {
    return '—';
  }

  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 6
  }).format(value);
}

function formatEntryType(entryType: string) {
  return entryType
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function formatCashTransferType(transferType: CashTransferType) {
  return transferType.charAt(0) + transferType.slice(1).toLowerCase();
}

function parseCashAmount(value: string) {
  const trimmedValue = value.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(trimmedValue)) {
    return 'Enter a dollar amount with cents only.';
  }

  const amount = Number(trimmedValue);
  if (!Number.isFinite(amount) || amount <= 0) {
    return 'Enter an amount greater than zero.';
  }
  if (amount > 1000000) {
    return 'Enter an amount of $1,000,000.00 or less.';
  }

  return amount;
}

function makeCashIdempotencyKey(transferType: CashTransferType) {
  const prefix = transferType === 'DEPOSIT' ? 'cash-deposit' : 'cash-withdrawal';
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `${prefix}:${crypto.randomUUID()}`;
  }

  return `${prefix}:${Date.now().toString(36)}:${Math.random().toString(36).slice(2, 10)}`;
}

function formatDateTime(timestamp: string) {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(timestamp));
}

function formatTime(timestamp: string) {
  return new Intl.DateTimeFormat('en-US', {
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(timestamp));
}

function portfolioMetricLabel(name: string) {
  const labels: Record<string, string> = {
    totalEquity: 'Total equity',
    cash: 'Cash',
    unrealizedPnl: 'Unrealized P&L'
  };

  return labels[name] ?? name;
}

function valueTone(value: number | null | undefined) {
  if (value == null || value === 0) {
    return undefined;
  }

  return value > 0 ? 'value-positive' : 'value-negative';
}
