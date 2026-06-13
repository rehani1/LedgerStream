import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, vi } from 'vitest';

import { AppRoutes } from './App';

describe('AppRoutes', () => {
  const symbols = [
    {
      id: '00000000-0000-0000-0000-000000000101',
      ticker: 'AAPL',
      name: 'Apple Inc.',
      exchange: 'NASDAQ',
      assetType: 'EQUITY',
      currency: 'USD',
      active: true
    },
    {
      id: '00000000-0000-0000-0000-000000000102',
      ticker: 'MSFT',
      name: 'Microsoft Corporation',
      exchange: 'NASDAQ',
      assetType: 'EQUITY',
      currency: 'USD',
      active: true
    }
  ];
  const quotes = {
    AAPL: {
      symbol: 'AAPL',
      timestamp: '2026-01-02T14:34:00Z',
      bid: 187.36,
      ask: 187.48,
      last: 187.42,
      volume: 136200,
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
  const authResponse = {
    accessToken: 'access-token',
    tokenType: 'Bearer',
    expiresAt: '2026-01-01T00:15:00Z',
    refreshToken: 'refresh-token',
    refreshTokenExpiresAt: '2026-01-08T00:00:00Z',
    user: {
      id: '00000000-0000-0000-0000-000000000001',
      email: 'demo@example.com',
      role: 'USER'
    }
  };

  beforeEach(() => {
    window.sessionStorage.clear();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);

        if (url.endsWith('/api/auth/login') || url.endsWith('/api/auth/register')) {
          return jsonResponse(authResponse);
        }

        if (url.endsWith('/api/auth/logout')) {
          return new Response(null, { status: 204 });
        }

        if (url.endsWith('/api/symbols')) {
          return jsonResponse(symbols);
        }

        if (url.includes('/api/symbols/AAPL/quote')) {
          return jsonResponse(quotes.AAPL);
        }

        if (url.includes('/api/symbols/MSFT/quote')) {
          return jsonResponse(quotes.MSFT);
        }

        if (url.includes('/api/symbols/AAPL/history')) {
          return jsonResponse({
            symbol: 'AAPL',
            range: '1d',
            limit: 100,
            ticks: [
              { ...quotes.AAPL, timestamp: '2026-01-02T14:30:00Z', last: 186.9 },
              quotes.AAPL
            ]
          });
        }

        if (url.includes('/api/stream/quotes')) {
          return sseResponse([
            'event: ready',
            'data: {"symbols":["AAPL","MSFT"],"connectedAt":"2026-01-02T14:34:01Z"}',
            '',
            'event: quote',
            'data: {"symbol":"AAPL","timestamp":"2026-01-02T14:35:00Z","bid":187.40,"ask":187.52,"last":187.46,"volume":136900,"source":"fixture"}',
            '',
            ''
          ].join('\n'));
        }

        return jsonResponse({ message: 'Unexpected request' }, 500);
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('redirects protected routes to sign in', async () => {
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: /primary/i })).toBeInTheDocument();
  });

  it('renders the register route', () => {
    render(
      <MemoryRouter initialEntries={['/auth/register']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(screen.getByRole('heading', { name: /create account/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /register/i })).toBeInTheDocument();
  });

  it('logs in, stores the token response, and signs out', async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={['/auth/login']}>
        <AppRoutes />
      </MemoryRouter>
    );

    await user.type(screen.getByLabelText(/email/i), 'demo@example.com');
    await user.type(screen.getByLabelText(/password/i), 'Password123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('heading', { name: /quote dashboard/i })).toBeInTheDocument();
    expect(await screen.findByText(/apple inc/i)).toBeInTheDocument();
    expect((await screen.findAllByText('$187.46')).length).toBeGreaterThan(0);
    expect(window.sessionStorage.getItem('ledgerstream.auth.session')).toContain('access-token');

    await user.click(screen.getByRole('button', { name: /sign out/i }));

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    expect(window.sessionStorage.getItem('ledgerstream.auth.session')).toBeNull();
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    headers: {
      'Content-Type': 'application/json'
    },
    status
  });
}

function sseResponse(body: string) {
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(body));
      controller.close();
    }
  });

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream'
    },
    status: 200
  });
}
