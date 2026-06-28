import {
  Activity,
  BarChart3,
  BriefcaseBusiness,
  CircleUserRound,
  LayoutDashboard,
  LogOut,
  ReceiptText,
  ShieldCheck
} from 'lucide-react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthContext';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/portfolio', label: 'Portfolio', icon: BriefcaseBusiness },
  { to: '/orders', label: 'Orders', icon: ReceiptText },
  { to: '/risk', label: 'Risk', icon: BarChart3 },
  { to: '/admin', label: 'Admin', icon: ShieldCheck, adminOnly: true }
];

export function AppLayout() {
  const auth = useAuth();
  const navigate = useNavigate();
  const visibleNavItems = navItems.filter((item) => !item.adminOnly || auth.user?.role === 'ADMIN');

  async function handleLogout() {
    await auth.logout();
    navigate('/auth/login', { replace: true });
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink className="brand" to="/dashboard" aria-label="LedgerStream dashboard">
          <Activity aria-hidden="true" size={24} />
          <span>LedgerStream</span>
        </NavLink>
        <nav className="primary-nav" aria-label="Primary">
          {visibleNavItems.map((item) => {
            const Icon = item.icon;

            return (
              <NavLink key={item.to} className="nav-link" to={item.to}>
                <Icon aria-hidden="true" size={18} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
        {auth.status === 'authenticated' ? (
          <div className="account-menu">
            <span className="account-email">{auth.user?.email}</span>
            <button type="button" className="icon-text-button" onClick={handleLogout}>
              <LogOut aria-hidden="true" size={18} />
              <span>Sign out</span>
            </button>
          </div>
        ) : (
          <NavLink className="account-link" to="/auth/login" aria-label="Account">
            <CircleUserRound aria-hidden="true" size={20} />
            <span>Sign in</span>
          </NavLink>
        )}
      </header>
      <main className="content-shell">
        <Outlet />
      </main>
    </div>
  );
}
