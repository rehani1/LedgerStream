import { Link } from 'react-router-dom';

type AuthPageProps = {
  mode: 'login' | 'register';
};

export function AuthPage({ mode }: AuthPageProps) {
  const isLogin = mode === 'login';

  return (
    <section className="page-stack auth-panel" aria-labelledby="auth-title">
      <div className="page-heading">
        <p className="eyebrow">Account</p>
        <h1 id="auth-title">{isLogin ? 'Sign in' : 'Create account'}</h1>
      </div>
      <form className="form-panel">
        <label>
          Email
          <input name="email" type="email" autoComplete="email" placeholder="demo@example.com" />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            autoComplete={isLogin ? 'current-password' : 'new-password'}
            placeholder="Password123!"
          />
        </label>
        <button type="button" className="primary-button">
          {isLogin ? 'Sign in' : 'Register'}
        </button>
      </form>
      <p className="switch-auth">
        {isLogin ? 'Need an account?' : 'Already registered?'}{' '}
        <Link to={isLogin ? '/auth/register' : '/auth/login'}>{isLogin ? 'Register' : 'Sign in'}</Link>
      </p>
    </section>
  );
}
