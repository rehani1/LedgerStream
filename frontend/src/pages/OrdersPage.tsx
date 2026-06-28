import { useEffect, useState, type FormEvent } from 'react';
import { Send, X } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { cancelOrder, createOrder, listOrders } from '../api/orders';
import { listSymbols } from '../api/quotes';
import type { CreateOrderRequest, OrderResponse, OrderSide, OrderStatus } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const orderSides: OrderSide[] = ['BUY', 'SELL'];

export function OrdersPage() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const [selectedSymbol, setSelectedSymbol] = useState('');
  const [side, setSide] = useState<OrderSide>('BUY');
  const [quantity, setQuantity] = useState('1');
  const [formError, setFormError] = useState<string | null>(null);
  const [lastOrder, setLastOrder] = useState<OrderResponse | null>(null);
  const [lastOrderMessage, setLastOrderMessage] = useState('Order accepted');
  const [cancelingOrderId, setCancelingOrderId] = useState<string | null>(null);

  const accessToken = auth.accessToken ?? '';
  const userId = auth.user?.id ?? 'anonymous';
  const queriesEnabled = auth.status === 'authenticated' && Boolean(auth.accessToken);

  const symbolsQuery = useQuery({
    queryKey: ['symbols'],
    queryFn: () => listSymbols(accessToken),
    enabled: queriesEnabled
  });

  const ordersQuery = useQuery({
    queryKey: ['orders', userId],
    queryFn: () => listOrders(accessToken),
    enabled: queriesEnabled
  });

  const symbols = symbolsQuery.data ?? [];
  const orders = ordersQuery.data ?? [];

  useEffect(() => {
    if (symbols.length === 0) {
      return;
    }

    if (!symbols.some((symbol) => symbol.ticker === selectedSymbol)) {
      setSelectedSymbol(symbols[0].ticker);
    }
  }, [selectedSymbol, symbols]);

  function invalidateTradingState() {
    queryClient.invalidateQueries({ queryKey: ['orders', userId] });
    queryClient.invalidateQueries({ queryKey: ['portfolio-summary', userId] });
    queryClient.invalidateQueries({ queryKey: ['portfolio-positions', userId] });
    queryClient.invalidateQueries({ queryKey: ['portfolio-ledger', userId] });
    queryClient.invalidateQueries({ queryKey: ['risk-latest', userId] });
    queryClient.invalidateQueries({ queryKey: ['risk-history', userId] });
  }

  const createOrderMutation = useMutation({
    mutationFn: (payload: { request: CreateOrderRequest; idempotencyKey: string }) =>
      createOrder(payload.request, accessToken, payload.idempotencyKey),
    onSuccess: (order) => {
      setLastOrder(order);
      setLastOrderMessage('Order accepted');
      setFormError(null);
      invalidateTradingState();
    }
  });

  const cancelOrderMutation = useMutation({
    mutationFn: (orderId: string) => cancelOrder(orderId, accessToken),
    onSuccess: (order) => {
      setLastOrder(order);
      setLastOrderMessage('Order cancelled');
      invalidateTradingState();
    },
    onSettled: () => {
      setCancelingOrderId(null);
    }
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const parsedQuantity = Number(quantity);
    const validationMessage = validateOrder(selectedSymbol, parsedQuantity);
    if (validationMessage) {
      setFormError(validationMessage);
      return;
    }

    setFormError(null);
    setLastOrder(null);
    createOrderMutation.mutate({
      request: {
        symbol: selectedSymbol,
        side,
        orderType: 'MARKET',
        quantity: parsedQuantity
      },
      idempotencyKey: makeIdempotencyKey()
    });
  }

  function handleCancel(orderId: string) {
    setCancelingOrderId(orderId);
    cancelOrderMutation.mutate(orderId);
  }

  const submitError = createOrderMutation.error instanceof Error ? createOrderMutation.error.message : null;
  const cancelError = cancelOrderMutation.error instanceof Error ? cancelOrderMutation.error.message : null;
  const isSubmitting = createOrderMutation.isPending;

  return (
    <section className="page-stack" aria-labelledby="orders-title">
      <div className="page-heading dashboard-heading">
        <div>
          <p className="eyebrow">Trading</p>
          <h1 id="orders-title">Orders</h1>
        </div>
        <span className="summary-timestamp">
          {ordersQuery.isFetching ? 'Refreshing' : `${orders.length} orders`}
        </span>
      </div>

      <div className="order-workspace">
        <form className="form-panel order-ticket" onSubmit={handleSubmit} aria-label="Order ticket" noValidate>
          <div className="table-heading compact-heading">
            <h2>Order Ticket</h2>
            <span>Market</span>
          </div>

          <label>
            Symbol
            <select
              value={selectedSymbol}
              onChange={(event) => setSelectedSymbol(event.target.value)}
              disabled={symbolsQuery.isLoading || symbols.length === 0 || isSubmitting}
            >
              {symbols.map((symbol) => (
                <option key={symbol.ticker} value={symbol.ticker}>
                  {symbol.ticker} - {symbol.name}
                </option>
              ))}
            </select>
          </label>

          <fieldset className="segmented-field">
            <legend>Side</legend>
            <div className="segmented-control">
              {orderSides.map((orderSide) => (
                <button
                  key={orderSide}
                  type="button"
                  className={side === orderSide ? 'active' : undefined}
                  aria-pressed={side === orderSide}
                  onClick={() => setSide(orderSide)}
                  disabled={isSubmitting}
                >
                  {orderSide}
                </button>
              ))}
            </div>
          </fieldset>

          <label>
            Quantity
            <input
              type="number"
              min="0.000001"
              step="0.000001"
              inputMode="decimal"
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
              disabled={isSubmitting}
            />
          </label>

          <button
            type="submit"
            className="primary-button icon-submit-button"
            disabled={isSubmitting || symbols.length === 0}
          >
            <Send aria-hidden="true" size={18} />
            <span>{isSubmitting ? 'Submitting' : 'Submit order'}</span>
          </button>

          {formError && <p className="form-error" role="alert">{formError}</p>}
          {submitError && <p className="form-error" role="alert">{submitError}</p>}
          {cancelError && <p className="form-error" role="alert">{cancelError}</p>}

          {lastOrder && (
            <div className="order-result" aria-live="polite">
              <span>{lastOrderMessage}</span>
              <strong>{lastOrder.symbol} {formatSide(lastOrder.side)} {formatQuantity(lastOrder.quantity)}</strong>
              <OrderStatusBadge status={lastOrder.status} />
              {lastOrder.rejectionReason && <small>{lastOrder.rejectionReason}</small>}
            </div>
          )}
        </form>

        <div className="table-panel">
          <div className="table-heading">
            <h2>Order History</h2>
            <span>{ordersQuery.isLoading ? 'Loading' : `${orders.length} records`}</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>Created</th>
                <th>Symbol</th>
                <th>Side</th>
                <th>Type</th>
                <th>Quantity</th>
                <th>Status</th>
                <th>Reason</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {ordersQuery.isLoading && (
                <tr>
                  <td colSpan={8}>Loading orders</td>
                </tr>
              )}
              {!ordersQuery.isLoading && orders.length === 0 && (
                <tr>
                  <td colSpan={8}>No orders submitted</td>
                </tr>
              )}
              {orders.map((order) => (
                <tr key={order.id}>
                  <td>{formatDateTime(order.createdAt)}</td>
                  <td>
                    <strong>{order.symbol}</strong>
                  </td>
                  <td>{formatSide(order.side)}</td>
                  <td>{formatOrderType(order.orderType)}</td>
                  <td>{formatQuantity(order.quantity)}</td>
                  <td>
                    <OrderStatusBadge status={order.status} />
                  </td>
                  <td>{order.rejectionReason ?? '—'}</td>
                  <td>
                    {order.status === 'PENDING' ? (
                      <button
                        type="button"
                        className="secondary-button icon-action-button"
                        onClick={() => handleCancel(order.id)}
                        disabled={cancelingOrderId === order.id}
                        aria-label={`Cancel ${order.symbol} order`}
                      >
                        <X aria-hidden="true" size={16} />
                        <span>{cancelingOrderId === order.id ? 'Cancelling' : 'Cancel'}</span>
                      </button>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`order-status status-${status.toLowerCase()}`}>{formatOrderStatus(status)}</span>;
}

function validateOrder(symbol: string, quantity: number) {
  if (!symbol) {
    return 'Select a symbol.';
  }

  if (!Number.isFinite(quantity) || quantity <= 0) {
    return 'Enter a quantity greater than zero.';
  }

  return null;
}

function makeIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `order:${crypto.randomUUID()}`;
  }

  return `order:${Date.now().toString(36)}:${Math.random().toString(36).slice(2, 10)}`;
}

function formatOrderStatus(status: OrderStatus) {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

function formatOrderType(orderType: string) {
  return orderType.charAt(0) + orderType.slice(1).toLowerCase();
}

function formatSide(orderSide: OrderSide) {
  return orderSide.charAt(0) + orderSide.slice(1).toLowerCase();
}

function formatQuantity(value: number) {
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 6
  }).format(value);
}

function formatDateTime(timestamp: string) {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(timestamp));
}
