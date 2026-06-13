export type UserRole = 'USER' | 'ADMIN';

export type CurrentUser = {
  id: string;
  email: string;
  role: UserRole;
};

export type SymbolSummary = {
  ticker: string;
  name: string;
  exchange: string;
  assetType: string;
  currency: string;
  active: boolean;
};

export type Quote = {
  symbol: string;
  timestamp: string;
  bid: number | null;
  ask: number | null;
  last: number;
  volume: number | null;
  source: string;
};
