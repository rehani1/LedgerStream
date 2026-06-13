import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, vi } from 'vitest';

import { AppRoutes } from './App';

describe('AppRoutes', () => {
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
          return jsonResponse([]);
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
