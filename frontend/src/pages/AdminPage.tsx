import { Play, RefreshCw, Square } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getQueueHealth, getReplayStatus, startReplay, stopReplay } from '../api/admin';
import type { ReplayControlResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';

export function AdminPage() {
  const auth = useAuth();
  const accessToken = auth.accessToken ?? '';
  const userId = auth.user?.id ?? 'anonymous';
  const isAdmin = auth.status === 'authenticated' && auth.user?.role === 'ADMIN';
  const queryClient = useQueryClient();
  const statusQueryKey = ['admin-replay-status', userId];

  const statusQuery = useQuery({
    queryKey: statusQueryKey,
    queryFn: () => getReplayStatus(accessToken),
    enabled: isAdmin && Boolean(accessToken),
    retry: false
  });

  const queueHealthQuery = useQuery({
    queryKey: ['admin-queue-health', userId],
    queryFn: () => getQueueHealth(accessToken),
    enabled: isAdmin && Boolean(accessToken),
    retry: false
  });

  const startMutation = useMutation({
    mutationFn: () => startReplay(accessToken),
    onSuccess: (response) => {
      queryClient.setQueryData(statusQueryKey, response);
    }
  });

  const stopMutation = useMutation({
    mutationFn: () => stopReplay(accessToken),
    onSuccess: (response) => {
      queryClient.setQueryData(statusQueryKey, response);
    }
  });

  const replayStatus = statusQuery.data;
  const queueTopics = Object.entries(queueHealthQuery.data?.topics ?? {});
  const actionPending = startMutation.isPending || stopMutation.isPending;
  const errorMessage = firstErrorMessage(statusQuery.error, queueHealthQuery.error, startMutation.error, stopMutation.error);

  if (!isAdmin) {
    return (
      <section className="page-stack" aria-labelledby="admin-title">
        <div className="page-heading">
          <p className="eyebrow">Admin</p>
          <h1 id="admin-title">Replay Controls</h1>
        </div>
        <div className="empty-panel error-panel" role="alert">
          <strong>Admin access required</strong>
          <span>Replay controls are available to admin accounts only.</span>
        </div>
      </section>
    );
  }

  return (
    <section className="page-stack" aria-labelledby="admin-title">
      <div className="page-heading dashboard-heading">
        <div>
          <p className="eyebrow">Admin</p>
          <h1 id="admin-title">Replay Controls</h1>
        </div>
        <span className="summary-timestamp">
          {replayStatus ? `Updated ${formatDateTime(replayStatus.updatedAt)}` : statusQuery.isLoading ? 'Loading' : 'Ready'}
        </span>
      </div>

      {errorMessage && (
        <div className="empty-panel error-panel" role="alert">
          <strong>Admin request failed</strong>
          <span>{errorMessage}</span>
        </div>
      )}

      <div className="admin-control-grid">
        <div className="table-panel control-panel">
          <div className="table-heading">
            <h2>Market Replay</h2>
            <ReplayStatusPill replayStatus={replayStatus} loading={statusQuery.isLoading} />
          </div>
          <div className="control-body">
            <div className="detail-tile">
              <span className="metric-label">Control mode</span>
              <strong>{replayStatus?.mode ?? '-'}</strong>
            </div>
            <div className="detail-tile">
              <span className="metric-label">State</span>
              <strong>{statusQuery.isLoading ? 'Loading' : replayStatus?.status ?? '-'}</strong>
            </div>
            <p className="control-message">{replayStatus?.message ?? 'Replay status is unavailable.'}</p>
            <div className="control-actions">
              <button
                type="button"
                className="primary-button"
                onClick={() => startMutation.mutate()}
                disabled={actionPending}
              >
                <Play aria-hidden="true" size={18} />
                <span>{startMutation.isPending ? 'Starting' : 'Start Replay'}</span>
              </button>
              <button
                type="button"
                className="secondary-button icon-action-button"
                onClick={() => stopMutation.mutate()}
                disabled={actionPending}
              >
                <Square aria-hidden="true" size={17} />
                <span>{stopMutation.isPending ? 'Stopping' : 'Stop Replay'}</span>
              </button>
              <button
                type="button"
                className="secondary-button icon-action-button"
                onClick={() => statusQuery.refetch()}
                disabled={statusQuery.isFetching}
              >
                <RefreshCw aria-hidden="true" size={17} />
                <span>{statusQuery.isFetching ? 'Refreshing' : 'Refresh'}</span>
              </button>
            </div>
          </div>
        </div>

        <div className="table-panel">
          <div className="table-heading">
            <h2>Queue Health</h2>
            <span>{queueHealthQuery.isFetching ? 'Loading' : queueHealthQuery.data?.status ?? 'Unavailable'}</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>Event</th>
                <th>Topic</th>
              </tr>
            </thead>
            <tbody>
              {queueHealthQuery.isLoading && (
                <tr>
                  <td colSpan={2}>Loading queue health</td>
                </tr>
              )}
              {!queueHealthQuery.isLoading && queueTopics.length === 0 && (
                <tr>
                  <td colSpan={2}>No topics reported</td>
                </tr>
              )}
              {queueTopics.map(([eventName, topic]) => (
                <tr key={eventName}>
                  <td>{formatTopicLabel(eventName)}</td>
                  <td>{topic}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function ReplayStatusPill({
  replayStatus,
  loading
}: {
  replayStatus: ReplayControlResponse | undefined;
  loading: boolean;
}) {
  if (loading) {
    return (
      <span className="status-pill status-connecting">
        <span aria-hidden="true" />
        Loading
      </span>
    );
  }

  const running = replayStatus?.status === 'RUNNING';

  return (
    <span className={`status-pill ${running ? 'status-live' : ''}`}>
      <span aria-hidden="true" />
      {replayStatus?.status ?? 'UNKNOWN'}
    </span>
  );
}

function firstErrorMessage(...errors: unknown[]) {
  const error = errors.find(Boolean);

  if (!error) {
    return null;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'Refresh the page or try again later.';
}

function formatDateTime(timestamp: string) {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(timestamp));
}

function formatTopicLabel(value: string) {
  return value.replace(/([A-Z])/g, ' $1').replace(/^./, (letter) => letter.toUpperCase());
}
