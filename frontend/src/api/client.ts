// specs/frontend/00-overview.md — thin fetch wrapper, /api base path, ApiError parsed from the
// {error, details} shape (specs/backend/00-overview.md). Default fetch credentials ('same-origin')
// already send the session cookie since the frontend is always same-origin with the API — see the
// dev-proxy note in vite.config.ts — so no explicit `credentials` option is needed here.

export class ApiError extends Error {
  readonly status: number;
  readonly details?: unknown;

  constructor(status: number, message: string, details?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

const BASE = '/api';

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(BASE + path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init.headers,
    },
  });

  if (response.status === 204) {
    return undefined as T;
  }

  if (!response.ok) {
    let error = response.statusText;
    let details: unknown;
    try {
      const body = await response.json();
      error = body.error ?? error;
      details = body.details;
    } catch {
      // body wasn't JSON (or was empty) — fall back to statusText, set above
    }
    throw new ApiError(response.status, error, details);
  }

  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export const api = {
  get: <T>(path: string): Promise<T> => request<T>(path),
  post: <T>(path: string, body?: unknown): Promise<T> =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown): Promise<T> =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown): Promise<T> =>
    request<T>(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: <T>(path: string): Promise<T> => request<T>(path, { method: 'DELETE' }),
};
