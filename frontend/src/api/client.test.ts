import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, getToken, setToken, tokenClaims } from './client';

const respond = (status: number, body?: unknown) =>
  vi.fn().mockResolvedValue(new Response(body === undefined ? null : JSON.stringify(body), { status }));

afterEach(() => {
  setToken(null);
  vi.unstubAllGlobals();
});

describe('api client', () => {
  it('sends the bearer token and JSON body', async () => {
    const fetchMock = respond(200, { ok: true });
    vi.stubGlobal('fetch', fetchMock);
    setToken('abc');
    await api('/api/v1/fleets', { body: { name: 'Vans' } });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/fleets');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer abc');
    expect(JSON.parse(init.body)).toEqual({ name: 'Vans' });
  });

  it('turns problem details into an ApiError with the server message and field errors', async () => {
    vi.stubGlobal('fetch', respond(400, { title: 'Bad Request', detail: 'Request validation failed', errors: { name: 'must not be blank' } }));
    const err = (await api('/api/v1/fleets', { body: {} }).catch((e) => e)) as ApiError;
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.message).toBe('Request validation failed');
    expect(err.problem.errors).toEqual({ name: 'must not be blank' });
  });

  it('signs out when an authenticated request comes back 401', async () => {
    vi.stubGlobal('fetch', respond(401, { title: 'Unauthorized' }));
    setToken('expired');
    await api('/api/v1/fleets').catch(() => undefined);
    expect(getToken()).toBeNull();
  });

  it('keeps the token when the login call itself fails', async () => {
    vi.stubGlobal('fetch', respond(401, { detail: 'Invalid credentials' }));
    setToken('still-valid');
    await api('/api/v1/auth/token', { body: { username: 'x', password: 'y' } }).catch(() => undefined);
    expect(getToken()).toBe('still-valid');
  });

  it('handles 204 No Content and query strings', async () => {
    const fetchMock = respond(204);
    vi.stubGlobal('fetch', fetchMock);
    await expect(api('/api/v1/x', { method: 'DELETE', query: { a: '1', b: 'two' } })).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/x?a=1&b=two');
  });

  it('reads display claims from a JWT and rejects garbage', () => {
    const payload = btoa(JSON.stringify({ sub: 'maria', roles: ['FLEET_MANAGER'] })).replace(/=+$/, '');
    expect(tokenClaims(`h.${payload}.s`)).toEqual({ username: 'maria', role: 'FLEET_MANAGER' });
    expect(tokenClaims('not-a-jwt')).toBeNull();
  });
});
