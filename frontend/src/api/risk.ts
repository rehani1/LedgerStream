import { apiRequest } from './client';
import type { RiskHistoryResponse, RiskSnapshot } from './types';

export function getLatestRisk(accessToken: string) {
  return apiRequest<RiskSnapshot>('/api/portfolio/risk', { accessToken });
}

export function getRiskHistory(accessToken: string, page = 0, size = 50) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size)
  });

  return apiRequest<RiskHistoryResponse>(`/api/portfolio/risk/history?${params}`, { accessToken });
}
