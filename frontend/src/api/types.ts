export type UserRole = 'USER' | 'ADMIN';

export type CurrentUser = {
  id: string;
  email: string;
  role: UserRole;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  user: CurrentUser;
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

export type QuoteHistoryResponse = {
  symbol: string;
  range: string;
  limit: number;
  ticks: Quote[];
};

export type QuoteStreamReady = {
  symbols: string[];
  connectedAt: string;
};

export type PortfolioSummary = {
  portfolioId: string;
  baseCurrency: string;
  cash: number;
  marketValue: number;
  totalEquity: number;
  realizedPnl: number;
  unrealizedPnl: number;
  positionsCount: number;
  pricedPositionsCount: number;
  updatedAt: string;
};

export type PortfolioPosition = {
  id: string;
  symbol: string;
  quantity: number;
  avgCost: number;
  lastPrice: number | null;
  valuationPrice: number;
  valuationSource: string;
  marketValue: number;
  costBasis: number;
  unrealizedPnl: number;
  realizedPnl: number;
  updatedAt: string;
};

export type LedgerEntryType =
  | 'INITIAL_DEPOSIT'
  | 'BUY_FILL'
  | 'SELL_FILL'
  | 'FEE'
  | 'REVERSAL'
  | 'ADJUSTMENT';

export type LedgerEntry = {
  id: string;
  entryType: LedgerEntryType;
  cashDelta: number;
  symbol: string | null;
  quantityDelta: number;
  price: number | null;
  orderId: string | null;
  fillId: string | null;
  createdAt: string;
  metadata: Record<string, unknown> | null;
};

export type LedgerPageResponse = {
  entries: LedgerEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type OrderSide = 'BUY' | 'SELL';

export type OrderType = 'MARKET' | 'LIMIT';

export type OrderStatus = 'PENDING' | 'FILLED' | 'CANCELLED' | 'REJECTED';

export type OrderResponse = {
  id: string;
  symbol: string;
  side: OrderSide;
  orderType: OrderType;
  quantity: number;
  limitPrice: number | null;
  status: OrderStatus;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateOrderRequest = {
  symbol: string;
  side: OrderSide;
  orderType: OrderType;
  quantity: number;
  limitPrice?: number | null;
};

export type RiskSnapshot = {
  id: string;
  totalEquity: number;
  cash: number;
  grossExposure: number;
  largestPositionPct: number;
  unrealizedPnl: number;
  createdAt: string;
};

export type RiskHistoryResponse = {
  snapshots: RiskSnapshot[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ReplayControlResponse = {
  status: 'RUNNING' | 'STOPPED';
  mode: string;
  message: string;
  updatedAt: string;
};

export type QueueHealthResponse = {
  status: string;
  checkedAt: string;
  topics: Record<string, string>;
};
