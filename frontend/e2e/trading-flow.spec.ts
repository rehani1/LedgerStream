import { expect, test, type Page, type Route } from '@playwright/test';

type SymbolSummary = {
  ticker: string;
  name: string;
  exchange: string;
  assetType: string;
  currency: string;
  active: boolean;
};

type Quote = {
  symbol: string;
  timestamp: string;
  bid: number | null;
  ask: number | null;
  last: number;
  volume: number | null;
  source: string;
};

const demoEmail = process.env.E2E_DEMO_EMAIL ?? 'demo@example.com';
const demoPassword = process.env.E2E_DEMO_PASSWORD ?? 'Password123!';
const useMockApi = process.env.E2E_MOCK_API !== 'false';

test('logs in, places a paper order, and verifies portfolio ledger updates', async ({ page }) => {
  if (useMockApi) {
    await installMockApi(page);
  }

  await page.goto('/auth/login');
  await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();

  await page.getByLabel(/email/i).fill(demoEmail);
  await page.getByLabel(/password/i).fill(demoPassword);
  await page.getByRole('button', { name: /sign in/i }).click();

  await expect(page.getByRole('heading', { name: /quote dashboard/i })).toBeVisible();
  await expect(page.getByText('Apple Inc.')).toBeVisible();
  await expect(page.getByText('$187.46').first()).toBeVisible();

  await page.getByRole('link', { name: /orders/i }).click();
  await expect(page.getByRole('heading', { name: 'Orders' })).toBeVisible();
  await page.getByLabel(/symbol/i).selectOption('AAPL');
  await page.getByLabel(/quantity/i).fill('2');
  await page.getByRole('button', { name: /submit order/i }).click();

  await expect(page.getByText('Order accepted')).toBeVisible();
  await expect(page.getByText(/AAPL\s+Buy\s+2/)).toBeVisible();
  await expect(page.getByText('Filled').first()).toBeVisible();

  await page.getByRole('link', { name: /portfolio/i }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Positions' })).toBeVisible();

  const summary = page.getByLabel(/portfolio summary/i);
  await expect(summary.getByText('$99,625.08')).toBeVisible();
  await expect(summary.getByText('$100,000.00')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Ledger' })).toBeVisible();
  await expect(page.getByText('Buy Fill')).toBeVisible();
  await expect(page.getByText('-$374.92')).toBeVisible();
});

async function installMockApi(page: Page) {
  const user = {
    id: '00000000-0000-0000-0000-000000000001',
    email: demoEmail,
    role: 'USER'
  };
  const symbols: SymbolSummary[] = [
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
  const quotes: Record<string, Quote> = {
    AAPL: {
      symbol: 'AAPL',
      timestamp: '2026-01-02T14:35:00Z',
      bid: 187.4,
      ask: 187.52,
      last: 187.46,
      volume: 136900,
      source: 'fixture'
    },
    MSFT: {
      symbol: 'MSFT',
      timestamp: '2026-01-02T14:35:00Z',
      bid: 421.12,
      ask: 421.3,
      last: 421.22,
      volume: 94400,
      source: 'fixture'
    }
  };
  let orders: unknown[] = [];
  let positions: unknown[] = [];
  let ledgerEntries: unknown[] = [];
  let portfolio = {
    portfolioId: '00000000-0000-0000-0000-000000000201',
    baseCurrency: 'USD',
    cash: 100000,
    marketValue: 0,
    totalEquity: 100000,
    realizedPnl: 0,
    unrealizedPnl: 0,
    positionsCount: 0,
    pricedPositionsCount: 0,
    updatedAt: '2026-01-02T14:35:00Z'
  };

  await page.route(/https?:\/\/(?:localhost|127\.0\.0\.1):8080\/api\/.*/, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;

    if (path === '/api/auth/login' && request.method() === 'POST') {
      return json(route, {
        accessToken: 'e2e-access-token',
        tokenType: 'Bearer',
        expiresAt: '2026-01-02T15:00:00Z',
        refreshToken: 'e2e-refresh-token',
        refreshTokenExpiresAt: '2026-01-09T15:00:00Z',
        user
      });
    }

    if (path === '/api/me') {
      return json(route, user);
    }

    if (path === '/api/symbols') {
      return json(route, symbols);
    }

    const quoteMatch = path.match(/^\/api\/symbols\/([^/]+)\/quote$/);
    if (quoteMatch) {
      return json(route, quotes[decodeURIComponent(quoteMatch[1])]);
    }

    const historyMatch = path.match(/^\/api\/symbols\/([^/]+)\/history$/);
    if (historyMatch) {
      const symbol = decodeURIComponent(historyMatch[1]);
      return json(route, {
        symbol,
        range: url.searchParams.get('range') ?? '1d',
        limit: Number(url.searchParams.get('limit') ?? 100),
        ticks: [
          { ...quotes[symbol], timestamp: '2026-01-02T14:30:00Z', last: quotes[symbol].last - 0.56 },
          quotes[symbol]
        ]
      });
    }

    if (path === '/api/stream/quotes') {
      return route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: [
          'event: ready',
          `data: ${JSON.stringify({ symbols: ['AAPL', 'MSFT'], connectedAt: '2026-01-02T14:35:01Z' })}`,
          '',
          'event: quote',
          `data: ${JSON.stringify(quotes.AAPL)}`,
          '',
          ''
        ].join('\n')
      });
    }

    if (path === '/api/orders' && request.method() === 'GET') {
      return json(route, orders);
    }

    if (path === '/api/orders' && request.method() === 'POST') {
      const payload = request.postDataJSON() as { symbol: string; side: 'BUY' | 'SELL'; quantity: number };
      const quote = quotes[payload.symbol];
      const fillValue = roundMoney(quote.last * payload.quantity);
      const createdAt = '2026-01-02T14:36:00Z';
      const order = {
        id: '00000000-0000-0000-0000-000000000501',
        symbol: payload.symbol,
        side: payload.side,
        orderType: 'MARKET',
        quantity: payload.quantity,
        limitPrice: null,
        status: 'FILLED',
        rejectionReason: null,
        createdAt,
        updatedAt: createdAt
      };

      orders = [order, ...orders];
      portfolio = {
        ...portfolio,
        cash: roundMoney(portfolio.cash - fillValue),
        marketValue: fillValue,
        totalEquity: 100000,
        positionsCount: 1,
        pricedPositionsCount: 1,
        updatedAt: createdAt
      };
      positions = [
        {
          id: '00000000-0000-0000-0000-000000000601',
          symbol: payload.symbol,
          quantity: payload.quantity,
          avgCost: quote.last,
          lastPrice: quote.last,
          valuationPrice: quote.last,
          valuationSource: 'REDIS',
          marketValue: fillValue,
          costBasis: fillValue,
          unrealizedPnl: 0,
          realizedPnl: 0,
          updatedAt: createdAt
        }
      ];
      ledgerEntries = [
        {
          id: '00000000-0000-0000-0000-000000000701',
          entryType: 'BUY_FILL',
          cashDelta: -fillValue,
          symbol: payload.symbol,
          quantityDelta: payload.quantity,
          price: quote.last,
          orderId: order.id,
          fillId: '00000000-0000-0000-0000-000000000801',
          createdAt,
          metadata: null
        }
      ];

      return json(route, order, 201);
    }

    if (path === '/api/portfolio') {
      return json(route, portfolio);
    }

    if (path === '/api/portfolio/positions') {
      return json(route, positions);
    }

    if (path === '/api/portfolio/ledger') {
      return json(route, {
        entries: ledgerEntries,
        page: Number(url.searchParams.get('page') ?? 0),
        size: Number(url.searchParams.get('size') ?? 10),
        totalElements: ledgerEntries.length,
        totalPages: ledgerEntries.length > 0 ? 1 : 0
      });
    }

    return json(route, { message: `Unhandled E2E mock route: ${request.method()} ${path}` }, 500);
  });
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body)
  });
}

function roundMoney(value: number) {
  return Math.round(value * 100) / 100;
}
