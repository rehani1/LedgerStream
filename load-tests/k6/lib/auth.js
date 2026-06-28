import http from 'k6/http';
import { fail } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export function getAuthToken() {
  if (__ENV.AUTH_TOKEN) {
    return __ENV.AUTH_TOKEN;
  }

  const email = __ENV.K6_EMAIL;
  const password = __ENV.K6_PASSWORD;
  if (!email || !password) {
    fail('Set AUTH_TOKEN or both K6_EMAIL and K6_PASSWORD before running k6.');
  }

  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json'
      },
      tags: {
        endpoint: 'auth-login'
      }
    }
  );

  if (response.status !== 200) {
    fail(`Unable to authenticate load-test user; login returned ${response.status}.`);
  }

  const token = response.json('accessToken');
  if (typeof token !== 'string' || token.length === 0) {
    fail('Login response did not include an access token.');
  }

  return token;
}

export function authHeaders(token, extraHeaders = {}) {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    ...extraHeaders
  };
}
