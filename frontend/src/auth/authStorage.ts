import type { AuthResponse, CurrentUser } from '../api/types';

const storageKey = 'ledgerstream.auth.session';

export type StoredAuthSession = {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  user: CurrentUser;
};

export function toStoredSession(response: AuthResponse): StoredAuthSession {
  return {
    accessToken: response.accessToken,
    tokenType: response.tokenType,
    expiresAt: response.expiresAt,
    refreshToken: response.refreshToken,
    refreshTokenExpiresAt: response.refreshTokenExpiresAt,
    user: response.user
  };
}

export function readStoredSession(): StoredAuthSession | null {
  try {
    const raw = window.sessionStorage.getItem(storageKey);

    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as Partial<StoredAuthSession>;

    if (!parsed.accessToken || !parsed.refreshToken || !parsed.user?.email) {
      return null;
    }

    return parsed as StoredAuthSession;
  } catch {
    return null;
  }
}

export function writeStoredSession(session: StoredAuthSession) {
  window.sessionStorage.setItem(storageKey, JSON.stringify(session));
}

export function clearStoredSession() {
  window.sessionStorage.removeItem(storageKey);
}
