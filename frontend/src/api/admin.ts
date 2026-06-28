import { apiRequest } from './client';
import type { QueueHealthResponse, ReplayControlResponse } from './types';

export function getReplayStatus(accessToken: string) {
  return apiRequest<ReplayControlResponse>('/api/admin/market/replay/status', { accessToken });
}

export function startReplay(accessToken: string) {
  return apiRequest<ReplayControlResponse>('/api/admin/market/replay/start', {
    accessToken,
    method: 'POST'
  });
}

export function stopReplay(accessToken: string) {
  return apiRequest<ReplayControlResponse>('/api/admin/market/replay/stop', {
    accessToken,
    method: 'POST'
  });
}

export function getQueueHealth(accessToken: string) {
  return apiRequest<QueueHealthResponse>('/api/admin/queue-health', { accessToken });
}
