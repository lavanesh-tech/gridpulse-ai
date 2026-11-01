import type { ProblemDetail } from './types';

/** Error carrying the API's RFC 9457 problem details, so screens can show the server's own explanation. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed (${status})`);
    this.status = status;
    this.problem = problem;
  }
}

const TOKEN_KEY = 'gridpulse.token';
let token: string | null = readStoredToken();
const listeners = new Set<() => void>();

function readStoredToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

/** The JWT lives in sessionStorage (cleared when the tab closes), never in localStorage or a cookie. */
export function setToken(value: string | null) {
  token = value;
  try {
    if (value) sessionStorage.setItem(TOKEN_KEY, value);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    // storage unavailable (private mode): keep the token in memory only
  }
  listeners.forEach((l) => l());
}

export function getToken() {
  return token;
}

export function onTokenChange(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export async function api<T>(path: string, init: { method?: string; body?: unknown; query?: Record<string, string> } = {}): Promise<T> {
  const url = init.query ? `${path}?${new URLSearchParams(init.query)}` : path;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (init.body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(url, {
    method: init.method ?? (init.body === undefined ? 'GET' : 'POST'),
    headers,
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });

  if (res.status === 401 && token && path !== '/api/v1/auth/token') {
    setToken(null); // expired or revoked: send the user back to sign-in
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) throw new ApiError(res.status, (data as ProblemDetail) ?? {});
  return data as T;
}

/** Reads role and username from the JWT payload for display only; the server enforces everything. */
export function tokenClaims(jwt: string | null): { username: string; role: string } | null {
  if (!jwt) return null;
  try {
    const payload = JSON.parse(atob(jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return { username: payload.sub, role: (payload.roles ?? [])[0] ?? 'VIEWER' };
  } catch {
    return null;
  }
}
