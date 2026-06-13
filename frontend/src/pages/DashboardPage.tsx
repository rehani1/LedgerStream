import { useQuery } from '@tanstack/react-query';

import { apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { SymbolSummary } from '../api/types';

export function DashboardPage() {
  const auth = useAuth();
  const symbolsQuery = useQuery({
    queryKey: ['symbols'],
    queryFn: () => apiRequest<SymbolSummary[]>('/api/symbols', {
      accessToken: auth.accessToken ?? undefined
    }),
    enabled: auth.status === 'authenticated' && Boolean(auth.accessToken)
  });

  return (
    <section className="page-stack" aria-labelledby="dashboard-title">
      <div className="page-heading">
        <p className="eyebrow">Markets</p>
        <h1 id="dashboard-title">Quote Dashboard</h1>
      </div>
      <div className="metric-grid" aria-label="Market summary">
        <article className="metric-card">
          <span className="metric-label">Symbols</span>
          <strong>{symbolsQuery.data?.length ?? '—'}</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Stream</span>
          <strong>Idle</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Last tick</span>
          <strong>—</strong>
        </article>
      </div>
      <div className="table-panel">
        <div className="table-heading">
          <h2>Symbols</h2>
          <span>{symbolsQuery.isFetching ? 'Loading' : 'Ready'}</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>Ticker</th>
              <th>Name</th>
              <th>Exchange</th>
              <th>Currency</th>
            </tr>
          </thead>
          <tbody>
            {(symbolsQuery.data ?? []).map((symbol) => (
              <tr key={symbol.ticker}>
                <td>{symbol.ticker}</td>
                <td>{symbol.name}</td>
                <td>{symbol.exchange}</td>
                <td>{symbol.currency}</td>
              </tr>
            ))}
            {!symbolsQuery.data?.length && (
              <tr>
                <td colSpan={4}>No symbols loaded</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
