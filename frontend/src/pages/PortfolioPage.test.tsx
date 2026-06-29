import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, vi } from 'vitest';

import { PortfolioPage } from './PortfolioPage';

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

describe('PortfolioPage', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);

        if (url.endsWith('/api/portfolio/cash/deposit')) {
          return jsonResponse({
            transferId: '00000000-0000-0000-0000-000000000801',
            transferType: 'DEPOSIT',
            amount: 1500,
            created: true,
            portfolio: {
              portfolioId: '00000000-0000-0000-0000-000000000201',
              baseCurrency: 'USD',
              cash: 51100,
              marketValue: 1874.2,
              totalEquity: 52974.2,
              realizedPnl: 124.5,
              unrealizedPnl: -12.8,
              positionsCount: 1,
              pricedPositionsCount: 1,
              updatedAt: '2026-01-02T14:45:00Z'
            },
            ledgerEntry: {
              id: '00000000-0000-0000-0000-000000000901',
              entryType: 'CASH_DEPOSIT',
              cashDelta: 1500,
              symbol: null,
              quantityDelta: 0,
              price: null,
              orderId: null,
              fillId: null,
              createdAt: '2026-01-02T14:45:00Z',
              metadata: {
                transferType: 'DEPOSIT'
              }
            }
          });
        }

        if (url.endsWith('/api/portfolio')) {
          return jsonResponse({
            portfolioId: '00000000-0000-0000-0000-000000000201',
            baseCurrency: 'USD',
            cash: 49600,
            marketValue: 1874.2,
            totalEquity: 51474.2,
            realizedPnl: 124.5,
            unrealizedPnl: -12.8,
            positionsCount: 1,
            pricedPositionsCount: 1,
            updatedAt: '2026-01-02T14:35:00Z'
          });
        }

        if (url.endsWith('/api/portfolio/positions')) {
          return jsonResponse([
            {
              id: '00000000-0000-0000-0000-000000000301',
              symbol: 'AAPL',
              quantity: 10,
              avgCost: 188.7,
              lastPrice: 187.42,
              valuationPrice: 187.42,
              valuationSource: 'REDIS',
              marketValue: 1874.2,
              costBasis: 1887,
              unrealizedPnl: -12.8,
              realizedPnl: 124.5,
              updatedAt: '2026-01-02T14:35:00Z'
            }
          ]);
        }

        if (url.includes('/api/portfolio/history')) {
          return jsonResponse({
            snapshots: [
              {
                id: '00000000-0000-0000-0000-000000000701',
                portfolioId: '00000000-0000-0000-0000-000000000201',
                totalEquity: 50000,
                cash: 50000,
                marketValue: 0,
                grossExposure: 0,
                realizedPnl: 0,
                unrealizedPnl: 0,
                createdAt: '2026-01-02T14:30:00Z'
              },
              {
                id: '00000000-0000-0000-0000-000000000702',
                portfolioId: '00000000-0000-0000-0000-000000000201',
                totalEquity: 51474.2,
                cash: 49600,
                marketValue: 1874.2,
                grossExposure: 1874.2,
                realizedPnl: 124.5,
                unrealizedPnl: -12.8,
                createdAt: '2026-01-02T14:35:00Z'
              }
            ],
            page: 0,
            size: 50,
            totalElements: 2,
            totalPages: 1
          });
        }

        if (url.includes('/api/portfolio/ledger') && url.includes('page=1')) {
          return jsonResponse({
            entries: [
              {
                id: '00000000-0000-0000-0000-000000000402',
                entryType: 'SELL_FILL',
                cashDelta: 562.26,
                symbol: 'AAPL',
                quantityDelta: -3,
                price: 187.42,
                orderId: '00000000-0000-0000-0000-000000000502',
                fillId: '00000000-0000-0000-0000-000000000602',
                createdAt: '2026-01-02T14:40:00Z',
                metadata: null
              }
            ],
            page: 1,
            size: 10,
            totalElements: 2,
            totalPages: 2
          });
        }

        if (url.includes('/api/portfolio/ledger')) {
          return jsonResponse({
            entries: [
              {
                id: '00000000-0000-0000-0000-000000000401',
                entryType: 'BUY_FILL',
                cashDelta: -1874.2,
                symbol: 'AAPL',
                quantityDelta: 10,
                price: 187.42,
                orderId: '00000000-0000-0000-0000-000000000501',
                fillId: '00000000-0000-0000-0000-000000000601',
                createdAt: '2026-01-02T14:35:00Z',
                metadata: null
              }
            ],
            page: 0,
            size: 10,
            totalElements: 2,
            totalPages: 2
          });
        }

        return jsonResponse({ message: 'Unexpected request' }, 500);
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders summary, positions, and ledger entries', async () => {
    renderPortfolioPage();

    const summary = await screen.findByLabelText(/portfolio summary/i);
    expect(await within(summary).findByText('$49,600.00')).toBeInTheDocument();
    expect(within(summary).getByText('$51,474.20')).toBeInTheDocument();
    expect(within(summary).getByText('$124.50')).toBeInTheDocument();
    expect(within(summary).getByText('-$12.80')).toBeInTheDocument();

    expect((await screen.findAllByText('AAPL')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('$1,874.20').length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', { name: /equity history/i })).toBeInTheDocument();
    expect(screen.getByText('2 points')).toBeInTheDocument();
    expect(screen.getByText('Buy Fill')).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
  });

  it('paginates ledger entries', async () => {
    const user = userEvent.setup();
    renderPortfolioPage();

    await screen.findByText('Buy Fill');
    await user.click(screen.getByRole('button', { name: /next/i }));

    expect(await screen.findByText('Sell Fill')).toBeInTheDocument();
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
  });

  it('submits a paper cash deposit', async () => {
    const user = userEvent.setup();
    renderPortfolioPage();

    await screen.findByRole('form', { name: /paper cash/i });
    const amountInput = screen.getByLabelText(/amount/i);
    await user.clear(amountInput);
    await user.type(amountInput, '1500.00');
    await user.click(screen.getByRole('button', { name: /deposit cash/i }));

    expect(await screen.findByText(/Deposit recorded/i)).toBeInTheDocument();
    expect(screen.getByText(/Cash balance \$51,100.00/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/portfolio/cash/deposit'),
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            amount: 1500,
            note: 'Demo paper funding'
          })
        })
      );
    });
  });
});

function renderPortfolioPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false
      }
    }
  });

  render(
    <QueryClientProvider client={queryClient}>
      <PortfolioPage />
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
