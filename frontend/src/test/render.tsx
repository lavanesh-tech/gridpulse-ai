import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { setToken } from '../api/client';

/** A JWT-shaped token whose payload the UI can read (signature is irrelevant client-side). */
export function fakeToken(role: string, username = 'maria') {
  const payload = btoa(JSON.stringify({ sub: username, roles: [role] })).replace(/=+$/, '');
  return `h.${payload}.s`;
}

type Route = { method?: string; path: string | RegExp; status?: number; body: unknown };

/** Minimal fake backend: first matching route wins; every request is recorded. */
export function mockApi(routes: Route[]) {
  const calls: { method: string; url: string; body: unknown }[] = [];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    const method = init.method ?? 'GET';
    const body = init.body ? JSON.parse(init.body as string) : undefined;
    calls.push({ method, url, body });
    const path = url.split('?')[0];
    const route = routes.find((r) => (r.method ?? 'GET') === method && (typeof r.path === 'string' ? r.path === path : r.path.test(path)));
    if (!route) return new Response(JSON.stringify({ detail: `no mock for ${method} ${path}` }), { status: 404 });
    return new Response(JSON.stringify(route.body), { status: route.status ?? 200 });
  }));
  return calls;
}

export function renderWithApp(ui: ReactElement, { role, route = '/' }: { role?: string; route?: string } = {}) {
  setToken(role ? fakeToken(role) : null);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}
