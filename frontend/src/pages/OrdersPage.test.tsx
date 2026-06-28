import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, vi } from 'vitest';

import { OrdersPage } from './OrdersPage';

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

const existingOrders = [
  {
    id: '00000000-0000-0000-0000-000000000101',
    symbol: 'MSFT',
    side: 'SELL',
    orderType: 'MARKET',
    quantity: 2,
    limitPrice: null,
    status: 'FILLED',
    rejectionReason: null,
    createdAt: '2026-01-02T14:30:00Z',
    updatedAt: '2026-01-02T14:31:00Z'
  }
];

const createdOrder = {
  id: '00000000-0000-0000-0000-000000000102',
  symbol: 'AAPL',
  side: 'BUY',
  orderType: 'MARKET',
  quantity: 3,
  limitPrice: null,
  status: 'PENDING',
  rejectionReason: null,
  createdAt: '2026-01-02T14:35:00Z',
  updatedAt: '2026-01-02T14:35:00Z'
};

const createdLimitOrder = {
  ...createdOrder,
  id: '00000000-0000-0000-0000-000000000103',
  orderType: 'LIMIT',
  quantity: 2,
  limitPrice: 180.25
};

describe('OrdersPage', () => {
  let createOrderResponse: Promise<Response> | null;

  beforeEach(() => {
    createOrderResponse = null;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);

        if (url.endsWith('/api/symbols')) {
          return jsonResponse(symbols);
        }

        if (url.endsWith('/api/orders') && init?.method === 'POST') {
          return createOrderResponse ?? jsonResponse(createdOrder, 201);
        }

        if (url.endsWith('/api/orders')) {
          return jsonResponse(existingOrders);
        }

        return jsonResponse({ message: 'Unexpected request' }, 500);
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('validates order quantity before submission', async () => {
    const user = userEvent.setup();
    renderOrdersPage();

    await screen.findByRole('combobox', { name: /symbol/i });
    await user.clear(screen.getByLabelText(/quantity/i));
    await user.type(screen.getByLabelText(/quantity/i), '0');
    await user.click(screen.getByRole('button', { name: /submit order/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter a quantity greater than zero.');
    expect(orderPostCalls()).toHaveLength(0);
  });

  it('submits market orders with an idempotency key and disables double-submit', async () => {
    const user = userEvent.setup();
    let resolveCreateOrder: (response: Response) => void;
    createOrderResponse = new Promise((resolve) => {
      resolveCreateOrder = resolve;
    });
    renderOrdersPage();

    await screen.findByRole('combobox', { name: /symbol/i });
    await user.clear(screen.getByLabelText(/quantity/i));
    await user.type(screen.getByLabelText(/quantity/i), '3');
    await user.click(screen.getByRole('button', { name: /submit order/i }));

    expect(await screen.findByRole('button', { name: /submitting/i })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: /submitting/i }));
    expect(orderPostCalls()).toHaveLength(1);

    resolveCreateOrder!(jsonResponse(createdOrder, 201));
    await screen.findByText(/order accepted/i);

    const postCalls = orderPostCalls();
    expect(postCalls).toHaveLength(1);
    expect(postCalls[0][1]?.headers).toMatchObject({
      'Idempotency-Key': expect.stringMatching(/^order:/)
    });
    expect(JSON.parse(String(postCalls[0][1]?.body))).toMatchObject({
      symbol: 'AAPL',
      side: 'BUY',
      orderType: 'MARKET',
      quantity: 3
    });
    expect(await screen.findByText('Pending')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('MSFT')).toBeInTheDocument();
    });
  });

  it('requires a positive limit price for limit orders', async () => {
    const user = userEvent.setup();
    renderOrdersPage();

    await screen.findByRole('combobox', { name: /symbol/i });
    await user.click(screen.getByRole('button', { name: /^limit$/i }));
    await user.click(screen.getByRole('button', { name: /submit order/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter a limit price greater than zero.');
    expect(orderPostCalls()).toHaveLength(0);
  });

  it('submits limit orders with a limit price', async () => {
    const user = userEvent.setup();
    createOrderResponse = Promise.resolve(jsonResponse(createdLimitOrder, 201));
    renderOrdersPage();

    await screen.findByRole('combobox', { name: /symbol/i });
    await user.click(screen.getByRole('button', { name: /^limit$/i }));
    await user.clear(screen.getByLabelText(/quantity/i));
    await user.type(screen.getByLabelText(/quantity/i), '2');
    await user.type(screen.getByLabelText(/limit price/i), '180.25');
    await user.click(screen.getByRole('button', { name: /submit order/i }));

    await screen.findByText(/order accepted/i);
    const postCalls = orderPostCalls();
    expect(postCalls).toHaveLength(1);
    expect(JSON.parse(String(postCalls[0][1]?.body))).toMatchObject({
      symbol: 'AAPL',
      side: 'BUY',
      orderType: 'LIMIT',
      quantity: 2,
      limitPrice: 180.25
    });
    expect(await screen.findByText(/Limit @ \$180\.25/)).toBeInTheDocument();
  });
});

function renderOrdersPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false
      },
      mutations: {
        retry: false
      }
    }
  });

  render(
    <QueryClientProvider client={queryClient}>
      <OrdersPage />
    </QueryClientProvider>
  );
}

function orderPostCalls() {
  return vi.mocked(fetch).mock.calls.filter(([input, init]) => {
    return String(input).endsWith('/api/orders') && init?.method === 'POST';
  });
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    headers: {
      'Content-Type': 'application/json'
    },
    status
  });
}
