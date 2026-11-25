// Typed HTTP client for the APAS backend (EP-10a). Same origin only (decision 4A): the session is the
// HttpOnly cookie set by the backend; this code never reads or stores it. A fresh CSRF token is fetched
// before every state-changing request (the backend renews it at login), and it lives only in memory for
// that one request.
//
// The backend answers errors as RFC 9457 problems. Only the fixed `code` and the server `traceId` are
// kept; the `title` and anything else in the body are dropped so no server text reaches the screen.

export type ApiErrorKind = 'http' | 'network' | 'timeout' | 'invalid-response';

const CODE = /^[A-Z][A-Z_]{0,63}$/;
const TRACE_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
// Spring Security's default CSRF header names (session store / cookie store). Any other name, such as
// Content-Type or Authorization, is refused, so a bad answer cannot reshape the write.
const CSRF_HEADER_NAMES: readonly string[] = ['X-CSRF-TOKEN', 'X-XSRF-TOKEN'];
const DEFAULT_TIMEOUT_MS = 15_000;

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number | null;
  readonly code: string | null;
  readonly traceId: string | null;
  /**
   * False when the request itself was never sent (the CSRF token read before a write failed), so the
   * screen may say "not sent" instead of "outcome unknown" (EP-10b review L-3).
   */
  readonly sent: boolean;

  constructor(kind: ApiErrorKind, status: number | null = null, code: string | null = null, traceId: string | null = null,
      sent = true) {
    super(`api ${kind}${status === null ? '' : ` ${status}`}`);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status;
    this.code = code;
    this.traceId = traceId;
    this.sent = sent;
  }

  /** The session is missing or expired: the app returns to the login page. */
  get unauthenticated(): boolean {
    return this.kind === 'http' && this.status === 401;
  }

  get forbidden(): boolean {
    return this.kind === 'http' && this.status === 403;
  }

  /** Short reference for support, from the server trace ID (writing guide §8): "REQ-1A2B3C4D". */
  get reference(): string | null {
    return this.traceId === null ? null : `REQ-${this.traceId.slice(0, 8).toUpperCase()}`;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST';
  /** JSON body. */
  json?: unknown;
  /** application/x-www-form-urlencoded body (login only). */
  form?: Record<string, string>;
  timeoutMs?: number;
}

/** Sends a request and returns the parsed JSON body, or undefined for 204. Throws ApiError otherwise. */
export async function request(path: string, options: RequestOptions = {}): Promise<unknown> {
  if (!path.startsWith('/api/')) {
    throw new ApiError('invalid-response');
  }
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  let body: string | undefined;
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.json);
  } else if (options.form !== undefined) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded';
    body = new URLSearchParams(options.form).toString();
  }
  if (method !== 'GET') {
    let csrf: Csrf;
    try {
      csrf = await fetchCsrf(options.timeoutMs);
    } catch (error) {
      if (error instanceof ApiError) {
        throw new ApiError(error.kind, error.status, error.code, error.traceId, false);
      }
      throw error;
    }
    headers[csrf.headerName] = csrf.token;
  }
  const response = await send(path, { method, headers, body }, options.timeoutMs);
  return readBody(response);
}

interface Csrf {
  headerName: string;
  token: string;
}

async function fetchCsrf(timeoutMs?: number): Promise<Csrf> {
  const response = await send('/api/auth/csrf', { method: 'GET', headers: { Accept: 'application/json' } }, timeoutMs);
  const value = await readBody(response);
  if (isRecord(value) && typeof value.headerName === 'string' && CSRF_HEADER_NAMES.includes(value.headerName)
      && typeof value.token === 'string' && value.token.length > 0 && value.token.length <= 512) {
    return { headerName: value.headerName, token: value.token };
  }
  throw new ApiError('invalid-response');
}

async function send(path: string, init: { method: string; headers: Record<string, string>; body?: string | undefined },
    timeoutMs = DEFAULT_TIMEOUT_MS): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(path, {
      method: init.method,
      headers: init.headers,
      body: init.body,
      credentials: 'same-origin',
      cache: 'no-store',
      redirect: 'error',
      signal: controller.signal,
    });
  } catch {
    throw new ApiError(controller.signal.aborted ? 'timeout' : 'network');
  } finally {
    clearTimeout(timer);
  }
}

async function readBody(response: Response): Promise<unknown> {
  if (response.ok) {
    if (response.status === 204) {
      return undefined;
    }
    try {
      return await response.json();
    } catch {
      throw new ApiError('invalid-response', response.status);
    }
  }
  let code: string | null = null;
  let traceId: string | null = null;
  try {
    const problem: unknown = await response.json();
    if (isRecord(problem)) {
      code = typeof problem.code === 'string' && CODE.test(problem.code) ? problem.code : null;
      traceId = typeof problem.traceId === 'string' && TRACE_ID.test(problem.traceId) ? problem.traceId : null;
    }
  } catch {
    // Not a problem body (proxy error page, empty body): status only.
  }
  throw new ApiError('http', response.status, code, traceId);
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
