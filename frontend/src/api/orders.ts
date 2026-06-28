import { apiRequest } from './client';
import type { CreateOrderRequest, OrderResponse } from './types';

export function listOrders(accessToken: string) {
  return apiRequest<OrderResponse[]>('/api/orders', { accessToken });
}

export function createOrder(request: CreateOrderRequest, accessToken: string, idempotencyKey: string) {
  return apiRequest<OrderResponse>('/api/orders', {
    method: 'POST',
    accessToken,
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify(request)
  });
}

export function cancelOrder(orderId: string, accessToken: string) {
  return apiRequest<OrderResponse>(`/api/orders/${encodeURIComponent(orderId)}/cancel`, {
    method: 'POST',
    accessToken
  });
}
