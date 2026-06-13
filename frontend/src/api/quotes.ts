import { apiBaseUrl, apiRequest } from './client';
import type { Quote, QuoteHistoryResponse, QuoteStreamReady, SymbolSummary } from './types';

type QuoteStreamOptions = {
  symbols: string[];
  accessToken: string;
  signal: AbortSignal;
  onReady: (ready: QuoteStreamReady) => void;
  onQuote: (quote: Quote) => void;
};

export function listSymbols(accessToken: string) {
  return apiRequest<SymbolSummary[]>('/api/symbols', { accessToken });
}

export function getLatestQuote(ticker: string, accessToken: string) {
  return apiRequest<Quote>(`/api/symbols/${encodeURIComponent(ticker)}/quote`, { accessToken });
}

export function getQuoteHistory(ticker: string, accessToken: string, range = '1d', limit = 100) {
  const params = new URLSearchParams({
    range,
    limit: String(limit)
  });

  return apiRequest<QuoteHistoryResponse>(`/api/symbols/${encodeURIComponent(ticker)}/history?${params}`, {
    accessToken
  });
}

export async function consumeQuoteStream(options: QuoteStreamOptions) {
  const params = new URLSearchParams({
    symbols: options.symbols.join(',')
  });
  const response = await fetch(`${apiBaseUrl}/api/stream/quotes?${params}`, {
    headers: {
      Accept: 'text/event-stream',
      Authorization: `Bearer ${options.accessToken}`
    },
    signal: options.signal
  });

  if (!response.ok) {
    throw new Error(`Quote stream failed with ${response.status}`);
  }

  if (!response.body) {
    throw new Error('Quote stream response did not include a body');
  }

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';

  while (!options.signal.aborted) {
    const result = await reader.read();

    if (result.done) {
      break;
    }

    buffer += result.value;
    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop() ?? '';

    for (const frame of frames) {
      dispatchSseFrame(frame, options);
    }
  }
}

function dispatchSseFrame(frame: string, options: QuoteStreamOptions) {
  let eventName = 'message';
  const dataLines: string[] = [];

  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith(':') || line.length === 0) {
      continue;
    }

    const separatorIndex = line.indexOf(':');
    const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex);
    const value = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1).trimStart();

    if (field === 'event') {
      eventName = value;
    }

    if (field === 'data') {
      dataLines.push(value);
    }
  }

  if (dataLines.length === 0) {
    return;
  }

  const data = JSON.parse(dataLines.join('\n')) as unknown;

  if (eventName === 'ready') {
    options.onReady(data as QuoteStreamReady);
  }

  if (eventName === 'quote') {
    options.onQuote(data as Quote);
  }
}
