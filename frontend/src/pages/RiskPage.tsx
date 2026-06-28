import { useMemo } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useQuery } from '@tanstack/react-query';

import { ApiError } from '../api/client';
import { getLatestRisk, getRiskHistory } from '../api/risk';
import { useAuth } from '../auth/AuthContext';

export function RiskPage() {
  const auth = useAuth();
  const accessToken = auth.accessToken ?? '';
  const userId = auth.user?.id ?? 'anonymous';
  const queriesEnabled = auth.status === 'authenticated' && Boolean(auth.accessToken);

  const latestRiskQuery = useQuery({
    queryKey: ['risk-latest', userId],
    queryFn: () => getLatestRisk(accessToken),
    enabled: queriesEnabled,
    retry: (failureCount, error) => !(error instanceof ApiError && error.status === 404) && failureCount < 1
  });

  const riskHistoryQuery = useQuery({
    queryKey: ['risk-history', userId],
    queryFn: () => getRiskHistory(accessToken, 0, 50),
    enabled: queriesEnabled
  });

  const latestRisk = latestRiskQuery.data;
  const history = riskHistoryQuery.data?.snapshots ?? [];
  const chartData = useMemo(() => {
    return [...history]
      .sort((left, right) => left.createdAt.localeCompare(right.createdAt))
      .map((snapshot) => ({
        time: formatTime(snapshot.createdAt),
        totalEquity: snapshot.totalEquity,
        grossExposure: snapshot.grossExposure,
        unrealizedPnl: snapshot.unrealizedPnl
      }));
  }, [history]);
  const noSnapshotYet = latestRiskQuery.error instanceof ApiError && latestRiskQuery.error.status === 404;
  const errorMessage = noSnapshotYet ? null : firstErrorMessage(latestRiskQuery.error, riskHistoryQuery.error);

  return (
    <section className="page-stack" aria-labelledby="risk-title">
      <div className="page-heading dashboard-heading">
        <div>
          <p className="eyebrow">Risk</p>
          <h1 id="risk-title">Exposure</h1>
        </div>
        <span className="summary-timestamp">
          {latestRisk ? `Updated ${formatDateTime(latestRisk.createdAt)}` : riskStatus(latestRiskQuery.isLoading, noSnapshotYet)}
        </span>
      </div>

      {errorMessage && (
        <div className="empty-panel error-panel" role="alert">
          <strong>Risk data unavailable</strong>
          <span>{errorMessage}</span>
        </div>
      )}

      {noSnapshotYet && (
        <div className="empty-panel">
          <strong>No risk snapshots</strong>
          <span>Risk metrics appear after a fill or market tick updates an open position.</span>
        </div>
      )}

      <div className="metric-grid risk-summary-grid" aria-label="Risk summary">
        <article className="metric-card">
          <span className="metric-label">Total equity</span>
          <strong>{latestRiskQuery.isLoading ? 'Loading' : formatMoney(latestRisk?.totalEquity)}</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Cash</span>
          <strong>{latestRiskQuery.isLoading ? 'Loading' : formatMoney(latestRisk?.cash)}</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Gross exposure</span>
          <strong>{latestRiskQuery.isLoading ? 'Loading' : formatMoney(latestRisk?.grossExposure)}</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Largest position</span>
          <strong>{latestRiskQuery.isLoading ? 'Loading' : formatPercent(latestRisk?.largestPositionPct)}</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Unrealized P&amp;L</span>
          <strong className={valueTone(latestRisk?.unrealizedPnl)}>
            {latestRiskQuery.isLoading ? 'Loading' : formatMoney(latestRisk?.unrealizedPnl)}
          </strong>
        </article>
      </div>

      <div className="risk-dashboard-grid">
        <div className="chart-panel">
          <div className="table-heading">
            <h2>Risk History</h2>
            <span>{riskHistoryQuery.isFetching ? 'Loading' : `${chartData.length} points`}</span>
          </div>
          <div className="chart-frame">
            {chartData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%" minWidth={320} minHeight={240}>
                <LineChart data={chartData} margin={{ top: 12, right: 20, bottom: 6, left: 0 }}>
                  <CartesianGrid stroke="#edf0eb" vertical={false} />
                  <XAxis dataKey="time" tickLine={false} axisLine={false} minTickGap={24} />
                  <YAxis
                    tickFormatter={(value) => formatCompactMoney(Number(value))}
                    tickLine={false}
                    axisLine={false}
                    width={72}
                  />
                  <Tooltip formatter={(value, name) => [formatMoney(Number(value)), riskMetricLabel(String(name))]} />
                  <Line type="monotone" dataKey="totalEquity" stroke="#116149" strokeWidth={2.5} dot={false} />
                  <Line type="monotone" dataKey="grossExposure" stroke="#6f4635" strokeWidth={2.2} dot={false} />
                  <Line type="monotone" dataKey="unrealizedPnl" stroke="#315d8a" strokeWidth={2.2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="chart-empty">No risk history</div>
            )}
          </div>
        </div>

        <div className="table-panel">
          <div className="table-heading">
            <h2>Recent Snapshots</h2>
            <span>{riskHistoryQuery.isFetching ? 'Loading' : `${history.length} rows`}</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Equity</th>
                <th>Exposure</th>
                <th>Largest</th>
                <th>Unrealized</th>
              </tr>
            </thead>
            <tbody>
              {riskHistoryQuery.isLoading && (
                <tr>
                  <td colSpan={5}>Loading snapshots</td>
                </tr>
              )}
              {!riskHistoryQuery.isLoading && history.length === 0 && (
                <tr>
                  <td colSpan={5}>No snapshots recorded</td>
                </tr>
              )}
              {history.map((snapshot) => (
                <tr key={snapshot.id}>
                  <td>{formatDateTime(snapshot.createdAt)}</td>
                  <td>{formatMoney(snapshot.totalEquity)}</td>
                  <td>{formatMoney(snapshot.grossExposure)}</td>
                  <td>{formatPercent(snapshot.largestPositionPct)}</td>
                  <td className={valueTone(snapshot.unrealizedPnl)}>{formatMoney(snapshot.unrealizedPnl)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function riskStatus(isLoading: boolean, noSnapshotYet: boolean) {
  if (isLoading) {
    return 'Loading';
  }

  return noSnapshotYet ? 'No snapshots' : 'Ready';
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

function formatMoney(value: number | null | undefined) {
  if (value == null) {
    return '-';
  }

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
}

function formatCompactMoney(value: number) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1
  }).format(value);
}

function formatPercent(value: number | null | undefined) {
  if (value == null) {
    return '-';
  }

  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value) + '%';
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

function riskMetricLabel(name: string) {
  const labels: Record<string, string> = {
    totalEquity: 'Total equity',
    grossExposure: 'Gross exposure',
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
