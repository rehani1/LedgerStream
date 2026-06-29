import { apiRequest } from './client';
import type {
  CashTransferRequest,
  CashTransferResponse,
  LedgerPageResponse,
  PortfolioHistoryResponse,
  PortfolioPosition,
  PortfolioSummary
} from './types';

export function getPortfolioSummary(accessToken: string) {
  return apiRequest<PortfolioSummary>('/api/portfolio', { accessToken });
}

export function listPortfolioPositions(accessToken: string) {
  return apiRequest<PortfolioPosition[]>('/api/portfolio/positions', { accessToken });
}

export function listLedgerEntries(accessToken: string, page = 0, size = 10) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size)
  });

  return apiRequest<LedgerPageResponse>(`/api/portfolio/ledger?${params}`, { accessToken });
}

export function getPortfolioHistory(accessToken: string, page = 0, size = 50) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size)
  });

  return apiRequest<PortfolioHistoryResponse>(`/api/portfolio/history?${params}`, { accessToken });
}

export function depositCash(request: CashTransferRequest, accessToken: string, idempotencyKey: string) {
  return cashTransfer('/api/portfolio/cash/deposit', request, accessToken, idempotencyKey);
}

export function withdrawCash(request: CashTransferRequest, accessToken: string, idempotencyKey: string) {
  return cashTransfer('/api/portfolio/cash/withdraw', request, accessToken, idempotencyKey);
}

function cashTransfer(
  path: string,
  request: CashTransferRequest,
  accessToken: string,
  idempotencyKey: string
) {
  return apiRequest<CashTransferResponse>(path, {
    method: 'POST',
    accessToken,
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify(request)
  });
}
