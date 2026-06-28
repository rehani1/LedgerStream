import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, vi } from 'vitest';

import type { Quote, QuoteStreamReady } from '../api/types';
import { DashboardPage } from './DashboardPage';

type StreamOptions = {
  symbols: string[];
  accessToken: string;
  signal: AbortSignal;
  onReady: (ready: QuoteStreamReady) => void;
  onQuote: (quote: Quote) => void;
};

const quoteApiMock = vi.hoisted(() => ({
  listSymbols: vi.fn(),
  getLatestQuote: vi.fn(),
  getQuoteHistory: vi.fn(),
  consumeQuoteStream: vi.fn()
}));

vi.mock('../api/quotes', () => quoteApiMock);

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

const symbols = [
  {
    ticker: 'AAPL',
    name: 'Apple Inc.',
    exchange: 'NASDAQ',
    assetType: 'EQUITY',
    currency: 'USD',
    active: true
  },
  {
    ticker: 'MSFT',
    name: 'Microsoft Corporation',
    exchange: 'NASDAQ',
    assetType: 'EQUITY',
    currency: 'USD',
    active: true
  }
];

const latestQuotes: Record<string, Quote> = {
  AAPL: {
    symbol: 'AAPL',
    timestamp: '2026-01-02T14:34:00Z',
    bid: 187.12,
    ask: 187.18,
    last: 187.15,
    volume: 125000,
    source: 'fixture'
  },
  MSFT: {
    symbol: 'MSFT',
    timestamp: '2026-01-02T14:34:00Z',
    bid: 421.12,
    ask: 421.3,
    last: 421.22,
    volume: 94400,
    source: 'fixture'
  }
};

describe('DashboardPage', () => {
  beforeEach(() => {
    quoteApiMock.listSymbols.mockReset();
    quoteApiMock.getLatestQuote.mockReset();
    quoteApiMock.getQuoteHistory.mockReset();
    quoteApiMock.consumeQuoteStream.mockReset();

    quoteApiMock.listSymbols.mockResolvedValue(symbols);
    quoteApiMock.getLatestQuote.mockImplementation((ticker: string) => Promise.resolve(latestQuotes[ticker]));
    quoteApiMock.getQuoteHistory.mockResolvedValue({
      symbol: 'AAPL',
      range: '1d',
      limit: 100,
      ticks: [
        {
          ...latestQuotes.AAPL,
          timestamp: '2026-01-02T14:30:00Z',
          last: 186.9
        },
        latestQuotes.AAPL
      ]
    });
  });

  it('renders quote dashboard data and stream lifecycle states', async () => {
    let streamOptions: StreamOptions | null = null;
    let closeStream: (() => void) | null = null;
    quoteApiMock.consumeQuoteStream.mockImplementation((options: StreamOptions) => {
      streamOptions = options;
      return new Promise<void>((resolve) => {
        closeStream = resolve;
      });
    });

    renderDashboardPage();

    expect(await screen.findByRole('heading', { name: /quote dashboard/i })).toBeInTheDocument();
    expect(await screen.findByText('Connecting')).toBeInTheDocument();
    expect(await screen.findByText('Apple Inc.')).toBeInTheDocument();

    const summary = screen.getByLabelText(/market summary/i);
    expect(within(summary).getByText('2')).toBeInTheDocument();
    expect(within(summary).getByText('AAPL')).toBeInTheDocument();
    expect(within(summary).getByText('$187.15')).toBeInTheDocument();

    expect(quoteApiMock.consumeQuoteStream).toHaveBeenCalledWith(
      expect.objectContaining({
        symbols: ['AAPL', 'MSFT'],
        accessToken: 'access-token'
      })
    );

    act(() => {
      streamOptions?.onReady({
        symbols: ['AAPL', 'MSFT'],
        connectedAt: '2026-01-02T14:34:01Z'
      });
    });
    expect(await screen.findByText('Live')).toBeInTheDocument();

    act(() => {
      streamOptions?.onQuote({
        ...latestQuotes.AAPL,
        timestamp: '2026-01-02T14:35:00Z',
        bid: 187.4,
        ask: 187.52,
        last: 187.46,
        volume: 136900
      });
    });

    expect((await screen.findAllByText('$187.46')).length).toBeGreaterThan(0);
    expect(screen.getByText(/Updated/)).toBeInTheDocument();

    await act(async () => {
      closeStream?.();
    });
    await waitFor(() => {
      expect(screen.getByText('Closed')).toBeInTheDocument();
    });
  });

  it('shows an error stream state when the mocked stream fails', async () => {
    quoteApiMock.consumeQuoteStream.mockRejectedValue(new Error('stream failed'));

    renderDashboardPage();

    expect(await screen.findByRole('heading', { name: /quote dashboard/i })).toBeInTheDocument();
    expect(await screen.findByText('Error')).toBeInTheDocument();
  });
});

function renderDashboardPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false
      }
    }
  });

  render(
    <QueryClientProvider client={queryClient}>
      <DashboardPage />
    </QueryClientProvider>
  );
}
