import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, vi } from 'vitest';

import { AdminPage } from './AdminPage';

const authMock = vi.hoisted(() => ({
  current: {
    status: 'authenticated',
    accessToken: 'access-token',
    user: {
      id: '00000000-0000-0000-0000-000000000001',
      email: 'admin@example.com',
      role: 'ADMIN'
    }
  }
}));

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => authMock.current
}));

const stoppedReplay = {
  status: 'STOPPED',
  mode: 'backend_state',
  message: 'Replay state is stopped.',
  updatedAt: '2026-01-02T14:30:00Z'
};

const runningReplay = {
  status: 'RUNNING',
  mode: 'backend_state',
  message: 'Replay state is running.',
  updatedAt: '2026-01-02T14:35:00Z'
};

describe('AdminPage', () => {
  beforeEach(() => {
    authMock.current = {
      status: 'authenticated',
      accessToken: 'access-token',
      user: {
        id: '00000000-0000-0000-0000-000000000001',
        email: 'admin@example.com',
        role: 'ADMIN'
      }
    };

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);

        if (url.endsWith('/api/admin/market/replay/status')) {
          return jsonResponse(stoppedReplay);
        }

        if (url.endsWith('/api/admin/market/replay/start') && init?.method === 'POST') {
          return jsonResponse(runningReplay);
        }

        if (url.endsWith('/api/admin/market/replay/stop') && init?.method === 'POST') {
          return jsonResponse(stoppedReplay);
        }

        if (url.endsWith('/api/admin/queue-health')) {
          return jsonResponse({
            status: 'topics_configured',
            checkedAt: '2026-01-02T14:30:00Z',
            topics: {
              marketTick: 'market.tick',
              orderCreated: 'order.created'
            }
          });
        }

        return jsonResponse({ message: 'Unexpected request' }, 500);
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders replay status and sends start and stop commands', async () => {
    const user = userEvent.setup();
    renderAdminPage();

    expect((await screen.findAllByText('STOPPED')).length).toBeGreaterThan(0);
    expect(screen.getByText('backend_state')).toBeInTheDocument();
    expect(await screen.findByText('market.tick')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /start replay/i }));
    expect((await screen.findAllByText('RUNNING')).length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: /stop replay/i }));
    expect((await screen.findAllByText('STOPPED')).length).toBeGreaterThan(0);

    expect(replayCommandCalls('/api/admin/market/replay/start')).toHaveLength(1);
    expect(replayCommandCalls('/api/admin/market/replay/stop')).toHaveLength(1);
  });

  it('hides replay controls from normal users', () => {
    authMock.current = {
      status: 'authenticated',
      accessToken: 'access-token',
      user: {
        id: '00000000-0000-0000-0000-000000000002',
        email: 'user@example.com',
        role: 'USER'
      }
    };

    renderAdminPage();

    expect(screen.getByText('Admin access required')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /start replay/i })).not.toBeInTheDocument();
  });
});

function renderAdminPage() {
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
      <AdminPage />
    </QueryClientProvider>
  );
}

function replayCommandCalls(path: string) {
  return vi.mocked(fetch).mock.calls.filter(([input, init]) => {
    return String(input).endsWith(path) && init?.method === 'POST';
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
