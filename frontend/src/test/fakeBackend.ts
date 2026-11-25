import axe from 'axe-core';
import { vi } from 'vitest';

// A fake of the backend HTTP surface for component tests: routes "METHOD /path" to a handler and records
// every call. Synthetic values only.

export interface RecordedCall {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: string | null;
  credentials: RequestCredentials | undefined;
}

export type Handler = (call: RecordedCall, signal: AbortSignal | null) => Response | Promise<Response>;

export const CSRF_TOKEN = 'synthetic-csrf-token-0001';
export const TRACE_ID = '1a2b3c4d-0000-4000-8000-00000000abcd';

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function problem(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ type: `urn:secure-erp-agent:problem:${code.toLowerCase()}`, title: 'Server title must not be shown', status, code, traceId: TRACE_ID, ...extra }),
    { status, headers: { 'Content-Type': 'application/problem+json' } });
}

export function networkFailure(): never {
  throw new TypeError('Failed to fetch');
}

/** Never answers; rejects when the client aborts (timeout). */
export const hang: Handler = (_call, signal) => new Promise<Response>((_resolve, reject) => {
  signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
});

export function fakeBackend(routes: Record<string, Handler>) {
  const calls: RecordedCall[] = [];
  const all: Record<string, Handler> = {
    'GET /api/auth/csrf': () => json(200, { headerName: 'X-CSRF-TOKEN', token: CSRF_TOKEN }),
    ...routes,
  };
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const headers: Record<string, string> = {};
    new Headers(init?.headers).forEach((value, key) => { headers[key] = value; });
    const call: RecordedCall = {
      method: init?.method ?? 'GET',
      path: String(input),
      headers,
      body: typeof init?.body === 'string' ? init.body : null,
      credentials: init?.credentials,
    };
    calls.push(call);
    // An exact route wins. A route registered as "METHOD /path?*" opts in to any query string on that path
    // (the test then checks the query itself); without the opt-in a query string is a different route.
    const query = call.path.indexOf('?');
    const handler = all[`${call.method} ${call.path}`]
      ?? (query >= 0 ? all[`${call.method} ${call.path.slice(0, query)}?*`] : undefined);
    if (!handler) {
      return problem(404, 'NOT_FOUND');
    }
    return handler(call, init?.signal ?? null);
  });
  vi.stubGlobal('fetch', fetchMock);
  return {
    calls,
    /** Calls other than the CSRF token reads. */
    apiCalls: () => calls.filter((c) => c.path !== '/api/auth/csrf'),
    set(route: string, handler: Handler) { all[route] = handler; },
  };
}

/** axe-core on the rendered DOM; colour contrast needs layout, which jsdom does not have. */
export async function axeViolations(node: Element): Promise<string[]> {
  const result = await axe.run(node, { rules: { 'color-contrast': { enabled: false } } });
  return result.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
}
