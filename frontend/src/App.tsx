import { Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { AuthProvider } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { AppLayout } from './layout/AppLayout';
import { AdminPage } from './pages/AdminPage';
import { AuthPage } from './pages/AuthPage';
import { DashboardPage } from './pages/DashboardPage';
import { OrdersPage } from './pages/OrdersPage';
import { PortfolioPage } from './pages/PortfolioPage';
import { RiskPage } from './pages/RiskPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 15_000
    }
  }
});

export function AppRoutes() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="auth/login" element={<AuthPage mode="login" />} />
            <Route path="auth/register" element={<AuthPage mode="register" />} />
            <Route element={<RequireAuth />}>
              <Route path="dashboard" element={<DashboardPage />} />
              <Route path="portfolio" element={<PortfolioPage />} />
              <Route path="orders" element={<OrdersPage />} />
              <Route path="risk" element={<RiskPage />} />
              <Route path="admin" element={<AdminPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Route>
        </Routes>
      </AuthProvider>
    </QueryClientProvider>
  );
}
