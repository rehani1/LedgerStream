import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, vi } from 'vitest';

import { AuthPage } from './AuthPage';

const authMock = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn()
}));

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    status: 'anonymous',
    user: null,
    accessToken: null,
    login: authMock.login,
    register: authMock.register,
    logout: authMock.logout
  })
}));

describe('AuthPage', () => {
  beforeEach(() => {
    authMock.login.mockReset();
    authMock.register.mockReset();
    authMock.logout.mockReset();
  });

  it('uses login form validation before submitting credentials', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/auth/login']}>
        <AuthPage mode="login" />
      </MemoryRouter>
    );

    const email = screen.getByLabelText(/email/i);
    const password = screen.getByLabelText(/password/i);
    const submit = screen.getByRole('button', { name: /sign in/i });

    await user.click(submit);

    expect(email).toBeInvalid();
    expect(password).toBeInvalid();
    expect(authMock.login).not.toHaveBeenCalled();

    await user.type(email, 'not-an-email');
    await user.type(password, 'Password123!');
    await user.click(submit);

    expect(email).toBeInvalid();
    expect(password).toHaveAttribute('minLength', '8');
    expect(authMock.login).not.toHaveBeenCalled();
  });
});
