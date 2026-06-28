import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

import { BASE_URL, authHeaders, getAuthToken } from './lib/auth.js';

const quoteApiDuration = new Trend('ledgerstream_quote_api_duration', true);
const quoteApiFailures = new Rate('ledgerstream_quote_api_failures');

const vus = Number(__ENV.K6_VUS || 5);
const duration = __ENV.K6_DURATION || '30s';
const symbols = (__ENV.K6_SYMBOLS || 'AAPL,MSFT,NVDA,TSLA,SPY')
  .split(',')
  .map((symbol) => symbol.trim().toUpperCase())
  .filter(Boolean);
const sleepSeconds = Number(__ENV.K6_SLEEP_SECONDS || 1);

export const options = {
  scenarios: {
    quote_api: {
      executor: 'constant-vus',
      vus,
      duration
    }
  },
  thresholds: {
    ledgerstream_quote_api_duration: ['p(95)<300'],
    ledgerstream_quote_api_failures: ['rate<0.01'],
    'http_reqs{endpoint:quote-api}': ['rate>1']
  }
};

export function setup() {
  if (symbols.length === 0) {
    throw new Error('K6_SYMBOLS must include at least one ticker.');
  }

  return {
    token: getAuthToken()
  };
}

export default function (data) {
  const symbol = symbols[__ITER % symbols.length];
  const response = http.get(`${BASE_URL}/api/symbols/${encodeURIComponent(symbol)}/quote`, {
    headers: authHeaders(data.token),
    tags: {
      endpoint: 'quote-api',
      symbol
    }
  });

  quoteApiDuration.add(response.timings.duration);
  const ok = check(response, {
    'quote returned successfully': (result) => result.status === 200,
    'quote response includes last price': (result) => hasJsonField(result, 'last')
  });
  quoteApiFailures.add(!ok);

  sleep(sleepSeconds);
}

function hasJsonField(response, field) {
  try {
    return response.json(field) !== undefined && response.json(field) !== null;
  } catch {
    return false;
  }
}
