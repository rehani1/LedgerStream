import { FormEvent, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';

import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

type AuthPageProps = {
  mode: 'login' | 'register';
};

export function AuthPage({ mode }: AuthPageProps) {
  const isLogin = mode === 'login';
  const auth = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const routeState = location.state as { from?: { pathname?: string } } | null;
  const nextPath = routeState?.from?.pathname ?? '/dashboard';

  if (auth.status === 'authenticated') {
    return <Navigate to="/dashboard" replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      if (isLogin) {
        await auth.login(email, password);
      } else {
        await auth.register(email, password);
      }
      navigate(nextPath, { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Authentication failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="page-stack auth-panel" aria-labelledby="auth-title">
      <div className="page-heading">
        <p className="eyebrow">Account</p>
        <h1 id="auth-title">{isLogin ? 'Sign in' : 'Create account'}</h1>
      </div>
      <form className="form-panel" onSubmit={handleSubmit}>
        <label>
          Email
          <input
            name="email"
            type="email"
            autoComplete="email"
            placeholder="demo@example.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            autoComplete={isLogin ? 'current-password' : 'new-password'}
            placeholder="Password123!"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            minLength={8}
            required
          />
        </label>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="primary-button" disabled={submitting}>
          {submitting ? 'Submitting' : isLogin ? 'Sign in' : 'Register'}
        </button>
      </form>
      <p className="switch-auth">
        {isLogin ? 'Need an account?' : 'Already registered?'}{' '}
        <Link to={isLogin ? '/auth/register' : '/auth/login'}>{isLogin ? 'Register' : 'Sign in'}</Link>
      </p>
    </section>
  );
}
