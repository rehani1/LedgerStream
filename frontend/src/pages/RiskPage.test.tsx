import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

import { RiskPage } from './RiskPage';

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    status: 'authenticated',
    accessToken: 'access-token',
    user: {
      id: '00000000-0000-0000-0000-000000000001',
      email: 'demo@example.com',
      role: 'USER'
    }
  })
}));

const latestRisk = {
  id: '00000000-0000-0000-0000-000000000401',
  totalEquity: 101250.5,
  cash: 98500,
  grossExposure: 3250.75,
  largestPositionPct: 2.74,
  unrealizedPnl: 250.5,
  createdAt: '2026-01-02T14:40:00Z'
};

const riskHistory = {
  snapshots: [
    latestRisk,
    {
      id: '00000000-0000-0000-0000-000000000402',
      totalEquity: 100950.25,
      cash: 98500,
      grossExposure: 2950.25,
      largestPositionPct: 2.52,
      unrealizedPnl: -49.75,
      createdAt: '2026-01-02T14:35:00Z'
    }
  ],
  page: 0,
  size: 50,
  totalElements: 2,
  totalPages: 1
};

describe('RiskPage', () => {
  let latestStatus: number;

  beforeEach(() => {
    latestStatus = 200;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);

        if (url.endsWith('/api/portfolio/risk')) {
          return latestStatus === 404
            ? jsonResponse({ message: 'Risk snapshot not found' }, 404)
            : jsonResponse(latestRisk);
        }

        if (url.includes('/api/portfolio/risk/history')) {
          return jsonResponse(latestStatus === 404 ? { ...riskHistory, snapshots: [], totalElements: 0, totalPages: 0 } : riskHistory);
        }

        return jsonResponse({ message: 'Unexpected request' }, 500);
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders latest risk metrics and historical rows', async () => {
    renderRiskPage();

    const summary = await screen.findByLabelText(/risk summary/i);
    expect(await within(summary).findByText('$101,250.50')).toBeInTheDocument();
    expect(within(summary).getByText('$98,500.00')).toBeInTheDocument();
    expect(within(summary).getByText('$3,250.75')).toBeInTheDocument();
    expect(within(summary).getByText('2.74%')).toBeInTheDocument();
    expect(within(summary).getByText('$250.50')).toBeInTheDocument();

    expect(await screen.findByRole('heading', { name: /risk history/i })).toBeInTheDocument();
    expect(screen.getByText('2 rows')).toBeInTheDocument();
    expect(screen.getByText('-$49.75')).toBeInTheDocument();
  });

  it('shows an empty state when no snapshots exist', async () => {
    latestStatus = 404;
    renderRiskPage();

    expect(await screen.findByText('No risk snapshots')).toBeInTheDocument();
    expect(screen.getByText('No snapshots recorded')).toBeInTheDocument();
  });
});

function renderRiskPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false
      }
    }
  });

  render(
    <QueryClientProvider client={queryClient}>
      <RiskPage />
    </QueryClientProvider>
  );
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    headers: {
      'Content-Type': 'application/json'
    },
    status
  });
}
