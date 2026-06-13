import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, vi } from 'vitest';

import { AppRoutes } from './App';

describe('AppRoutes', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify([]), {
        headers: {
          'Content-Type': 'application/json'
        },
        status: 200
      }))
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the dashboard shell by default', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: /quote dashboard/i })).toBeInTheDocument();
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
});
