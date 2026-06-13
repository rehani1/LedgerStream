import { apiRequest } from './client';
import type { AuthResponse, CurrentUser } from './types';

export type AuthCredentials = {
  email: string;
  password: string;
};

export function login(credentials: AuthCredentials) {
  return apiRequest<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials)
  });
}

export function register(credentials: AuthCredentials) {
  return apiRequest<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(credentials)
  });
}

export function refresh(refreshToken: string) {
  return apiRequest<AuthResponse>('/api/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken })
  });
}

export function logout(refreshToken: string) {
  return apiRequest<void>('/api/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken })
  });
}

export function currentUser(accessToken: string) {
  return apiRequest<CurrentUser>('/api/me', {
    accessToken
  });
}
