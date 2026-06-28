import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

import { BASE_URL, authHeaders, getAuthToken } from './lib/auth.js';

const orderCreateDuration = new Trend('ledgerstream_order_create_duration', true);
const orderCreateFailures = new Rate('ledgerstream_order_create_failures');

const vus = Number(__ENV.K6_VUS || 1);
const duration = __ENV.K6_DURATION || '30s';
const symbol = __ENV.K6_SYMBOL || 'AAPL';
const side = __ENV.K6_SIDE || 'BUY';
const quantity = Number(__ENV.K6_ORDER_QUANTITY || 1);
const sleepSeconds = Number(__ENV.K6_SLEEP_SECONDS || 1);

export const options = {
  scenarios: {
    order_create: {
      executor: 'constant-vus',
      vus,
      duration
    }
  },
  thresholds: {
    ledgerstream_order_create_duration: ['p(95)<750'],
    ledgerstream_order_create_failures: ['rate<0.01'],
    'http_reqs{endpoint:order-create}': ['rate>0.5']
  }
};

export function setup() {
  return {
    token: getAuthToken()
  };
}

export default function (data) {
  const payload = {
    symbol,
    side,
    orderType: 'MARKET',
    quantity
  };
  const response = http.post(`${BASE_URL}/api/orders`, JSON.stringify(payload), {
    headers: authHeaders(data.token, {
      'Idempotency-Key': makeIdempotencyKey()
    }),
    tags: {
      endpoint: 'order-create'
    }
  });

  orderCreateDuration.add(response.timings.duration);
  const ok = check(response, {
    'order creation accepted': (result) => result.status === 200 || result.status === 201,
    'order response includes id': (result) => hasJsonField(result, 'id')
  });
  orderCreateFailures.add(!ok);

  sleep(sleepSeconds);
}

function makeIdempotencyKey() {
  return `k6-order-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function hasJsonField(response, field) {
  try {
    return Boolean(response.json(field));
  } catch {
    return false;
  }
}
