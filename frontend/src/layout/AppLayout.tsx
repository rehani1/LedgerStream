import { Activity, BarChart3, BriefcaseBusiness, CircleUserRound, LayoutDashboard, ReceiptText } from 'lucide-react';
import { NavLink, Outlet } from 'react-router-dom';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/portfolio', label: 'Portfolio', icon: BriefcaseBusiness },
  { to: '/orders', label: 'Orders', icon: ReceiptText },
  { to: '/risk', label: 'Risk', icon: BarChart3 }
];

export function AppLayout() {
  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink className="brand" to="/dashboard" aria-label="LedgerStream dashboard">
          <Activity aria-hidden="true" size={24} />
          <span>LedgerStream</span>
        </NavLink>
        <nav className="primary-nav" aria-label="Primary">
          {navItems.map((item) => {
            const Icon = item.icon;

            return (
              <NavLink key={item.to} className="nav-link" to={item.to}>
                <Icon aria-hidden="true" size={18} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
        <NavLink className="account-link" to="/auth/login" aria-label="Account">
          <CircleUserRound aria-hidden="true" size={20} />
          <span>Account</span>
        </NavLink>
      </header>
      <main className="content-shell">
        <Outlet />
      </main>
    </div>
  );
}
