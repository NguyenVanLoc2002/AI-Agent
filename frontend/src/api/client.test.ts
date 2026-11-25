import { describe, expect, it } from 'vitest';
import { CSRF_TOKEN, fakeBackend, hang, json, networkFailure, problem, TRACE_ID } from '../test/fakeBackend';
import { ApiError, request } from './client';

describe('request', () => {
  it('reads with the session cookie of the same origin and without a CSRF round trip', async () => {
    const backend = fakeBackend({ 'GET /api/demo/x': () => json(200, { ok: true }) });

    await expect(request('/api/demo/x')).resolves.toEqual({ ok: true });

    expect(backend.calls).toHaveLength(1);
    expect(backend.calls[0]?.credentials).toBe('same-origin');
    expect(backend.calls[0]?.headers['x-csrf-token']).toBeUndefined();
  });

  it('fetches a fresh CSRF token before every write and keeps it out of browser storage', async () => {
    const backend = fakeBackend({ 'POST /api/demo/x': () => json(200, {}) });

    await request('/api/demo/x', { method: 'POST', json: { a: 1 } });
    await request('/api/demo/x', { method: 'POST', json: { a: 2 } });

    expect(backend.calls.map((c) => `${c.method} ${c.path}`)).toEqual([
      'GET /api/auth/csrf', 'POST /api/demo/x', 'GET /api/auth/csrf', 'POST /api/demo/x',
    ]);
    expect(backend.apiCalls().every((c) => c.headers['x-csrf-token'] === CSRF_TOKEN)).toBe(true);
    expect(backend.apiCalls()[0]?.body).toBe('{"a":1}');
    expect(window.localStorage.length + window.sessionStorage.length).toBe(0);
    expect(document.cookie).toBe('');
  });

  it('does not send the write when the CSRF answer is malformed', async () => {
    const backend = fakeBackend({
      'GET /api/auth/csrf': () => json(200, { headerName: 'Content-Type', token: 't' }),
      'POST /api/demo/x': () => json(200, {}),
    });

    await expect(request('/api/demo/x', { method: 'POST' })).rejects.toMatchObject({ kind: 'invalid-response' });
    for (const headerName of ['Authorization', 'Bad Header: x', 'x-csrf-token']) {
      backend.set('GET /api/auth/csrf', () => json(200, { headerName, token: 't' }));
      await expect(request('/api/demo/x', { method: 'POST' })).rejects.toMatchObject({ kind: 'invalid-response' });
    }
    expect(backend.apiCalls()).toHaveLength(0);
  });

  it('keeps only the fixed code and the trace ID of a problem, never the server title', async () => {
    fakeBackend({ 'GET /api/demo/x': () => problem(409, 'QUOTE_STALE_VERSION', { detail: 'secret detail' }) });

    const error = await request('/api/demo/x').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError).toMatchObject({ kind: 'http', status: 409, code: 'QUOTE_STALE_VERSION', traceId: TRACE_ID });
    expect(apiError.reference).toBe('REQ-1A2B3C4D');
    expect(JSON.stringify(apiError)).not.toContain('secret detail');
    expect(apiError.message).not.toContain('Server title');
  });

  it('drops a code or trace ID that does not have the expected shape', async () => {
    fakeBackend({ 'GET /api/demo/x': () => json(500, { code: '<img src=x>', traceId: 'not-a-uuid' }) });

    await expect(request('/api/demo/x')).rejects.toMatchObject({ kind: 'http', status: 500, code: null, traceId: null });
  });

  it('reports an error page that is not JSON by its status only', async () => {
    fakeBackend({ 'GET /api/demo/x': () => new Response('<html>Bad gateway</html>', { status: 502 }) });

    await expect(request('/api/demo/x')).rejects.toMatchObject({ kind: 'http', status: 502, code: null });
  });

  it('tells a network failure from a timeout', async () => {
    fakeBackend({ 'GET /api/demo/down': () => networkFailure(), 'GET /api/demo/slow': hang });

    await expect(request('/api/demo/down')).rejects.toMatchObject({ kind: 'network' });
    await expect(request('/api/demo/slow', { timeoutMs: 20 })).rejects.toMatchObject({ kind: 'timeout' });
  });

  it('refuses a path outside the API without sending anything', async () => {
    const backend = fakeBackend({});

    await expect(request('https://evil.example/api/x')).rejects.toMatchObject({ kind: 'invalid-response' });
    await expect(request('//evil.example/api/x')).rejects.toMatchObject({ kind: 'invalid-response' });
    expect(backend.calls).toHaveLength(0);
  });

  it('answers 204 with no body', async () => {
    fakeBackend({ 'POST /api/auth/logout': () => new Response(null, { status: 204 }) });

    await expect(request('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined();
  });
});
