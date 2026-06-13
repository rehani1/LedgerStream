import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import * as authApi from '../api/auth';
import type { AuthResponse, CurrentUser } from '../api/types';
import {
  clearStoredSession,
  readStoredSession,
  toStoredSession,
  type StoredAuthSession,
  writeStoredSession
} from './authStorage';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

type AuthState = {
  status: AuthStatus;
  session: StoredAuthSession | null;
};

type AuthContextValue = {
  status: AuthStatus;
  user: CurrentUser | null;
  accessToken: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => {
    const session = readStoredSession();

    return {
      status: session ? 'loading' : 'anonymous',
      session
    };
  });

  const applyAuthResponse = useCallback((response: AuthResponse) => {
    const session = toStoredSession(response);
    writeStoredSession(session);
    setState({ status: 'authenticated', session });
  }, []);

  const clearAuth = useCallback(() => {
    clearStoredSession();
    setState({ status: 'anonymous', session: null });
  }, []);

  useEffect(() => {
    if (state.status !== 'loading' || !state.session) {
      return;
    }

    let active = true;
    const session = state.session;

    async function verifyStoredSession() {
      try {
        const user = await authApi.currentUser(session.accessToken);
        if (!active) {
          return;
        }

        const verifiedSession = { ...session, user };
        writeStoredSession(verifiedSession);
        setState({ status: 'authenticated', session: verifiedSession });
      } catch {
        try {
          const refreshed = await authApi.refresh(session.refreshToken);
          if (active) {
            applyAuthResponse(refreshed);
          }
        } catch {
          if (active) {
            clearAuth();
          }
        }
      }
    }

    verifyStoredSession();

    return () => {
      active = false;
    };
  }, [applyAuthResponse, clearAuth, state.session, state.status]);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await authApi.login({ email, password });
      applyAuthResponse(response);
    },
    [applyAuthResponse]
  );

  const register = useCallback(
    async (email: string, password: string) => {
      const response = await authApi.register({ email, password });
      applyAuthResponse(response);
    },
    [applyAuthResponse]
  );

  const logout = useCallback(async () => {
    const refreshToken = state.session?.refreshToken;
    clearAuth();

    if (refreshToken) {
      await authApi.logout(refreshToken).catch(() => undefined);
    }
  }, [clearAuth, state.session?.refreshToken]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status: state.status,
      user: state.session?.user ?? null,
      accessToken: state.session?.accessToken ?? null,
      login,
      register,
      logout
    }),
    [login, logout, register, state.session?.accessToken, state.session?.user, state.status]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }

  return context;
}
