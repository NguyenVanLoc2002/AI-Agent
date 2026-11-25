import { expect, type Browser, type Page } from '@playwright/test';
import { BASE_URL } from './env';

// FE-E2E (A-71) helpers. Synthetic demo users of application-demo.yml only. The password is read from the
// environment at call time and only typed into the password field or sent in the login form; it never
// appears in a test name, an assertion message or a file.

export type DemoUser = 'ops-1' | 'sales-1' | 'secrev-1' | 'cust-a';

export function demoPassword(): string {
  const password = process.env.APAS_DEMO_PASSWORD;
  if (!password) throw new Error('APAS_DEMO_PASSWORD is not set');
  return password;
}

/** Signs in through the login form, like a person. */
export async function signInWithForm(page: Page, username: string, password: string = demoPassword()): Promise<void> {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible();
  await page.getByLabel('Tên đăng nhập').fill(username);
  await page.getByLabel('Mật khẩu').fill(password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
}

export interface ApiAnswer {
  status: number;
  /** Problem `code` of an error answer, if any. The body itself is not returned to the test. */
  code: string | null;
  /** Top-level keys of a JSON object body, sorted (empty otherwise), to compare two answers by shape. */
  keys: string[];
}

/**
 * The fields the backend writes in an error answer (`platform/ProblemResponses`). An error answer must carry
 * these and nothing else (review security L4: an allowlist, not a pattern search for stack traces).
 */
const PROBLEM_KEYS = ['code', 'status', 'title', 'traceId', 'type'];

export function expectProblem(answer: ApiAnswer, status: number, code: string): void {
  expect(answer.status).toBe(status);
  expect(answer.code).toBe(code);
  expect(answer.keys.filter((key) => !PROBLEM_KEYS.includes(key)), 'fields outside the problem allowlist').toEqual([]);
}

interface Request {
  method: 'GET' | 'POST';
  path: string;
  json?: unknown;
  form?: Record<string, string>;
  withCsrf: boolean;
}

/** Runs in the page: same origin, the page's own session cookie, the Vite proxy, like the app's requests. */
async function send(page: Page, request: Request): Promise<ApiAnswer> {
  return page.evaluate(async ({ method, path, json, form, withCsrf }) => {
    const headers: Record<string, string> = { Accept: 'application/json' };
    let body: string | undefined;
    if (json !== undefined) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(json);
    } else if (form !== undefined) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
      body = new URLSearchParams(form).toString();
    }
    if (withCsrf) {
      const csrf = await fetch('/api/auth/csrf', { headers: { Accept: 'application/json' }, redirect: 'error' });
      const token = (await csrf.json()) as { headerName: string; token: string };
      headers[token.headerName] = token.token;
    }
    const response = await fetch(path, { method, headers, body, redirect: 'error' });
    const text = await response.text();
    let code: string | null = null;
    let keys: string[] = [];
    try {
      const parsed = JSON.parse(text) as unknown;
      if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
        const record = parsed as Record<string, unknown>;
        code = typeof record.code === 'string' ? record.code : null;
        keys = Object.keys(record).sort();
      }
    } catch {
      code = null;
    }
    return { status: response.status, code, keys };
  }, request);
}

/**
 * Calls the API from inside the page. A write first reads a fresh CSRF token, as the app does, unless
 * `withCsrf` is false (to show the backend refuses a write without one).
 */
export async function callApi(page: Page, method: 'GET' | 'POST', path: string, json?: unknown, withCsrf = method !== 'GET'): Promise<ApiAnswer> {
  return send(page, { method, path, json, withCsrf });
}

/** Sends the login form from the page; returns status, code and shape only (review QA H2). */
export async function loginAnswer(page: Page, username: string, password: string): Promise<ApiAnswer> {
  return send(page, { method: 'POST', path: '/api/auth/login', form: { username, password }, withCsrf: true });
}

/** Signs in without the form, for setup and clean-up steps. */
export async function signInWithApi(page: Page, username: DemoUser): Promise<void> {
  await page.goto('/');
  const answer = await loginAnswer(page, username, demoPassword());
  expect(answer.status, `sign-in of ${username}`).toBe(200);
}

/** Reads the AI switch with the page's session (OPS only, else the read fails). */
async function readAiSwitch(page: Page): Promise<boolean> {
  return page.evaluate(async () => {
    const response = await fetch('/api/ops/kill-switch', { headers: { Accept: 'application/json' }, redirect: 'error' });
    if (!response.ok) throw new Error(`kill switch read ${response.status}`);
    const body = (await response.json()) as { aiEnabled: unknown };
    if (typeof body.aiEnabled !== 'boolean') throw new Error('kill switch body unreadable');
    return body.aiEnabled;
  });
}

/** Runs `action` with a fresh OPS session in its own browser context, never the page under test. */
async function asOps<T>(browser: Browser, action: (page: Page) => Promise<T>): Promise<T> {
  // baseURL explicitly: the global teardown passes a plain browser without the test options.
  const context = await browser.newContext({ baseURL: BASE_URL });
  try {
    const page = await context.newPage();
    await signInWithApi(page, 'ops-1');
    return await action(page);
  } finally {
    await context.close();
  }
}

/** Reads the AI switch as OPS. */
export async function readAiSwitchAsOps(browser: Browser): Promise<boolean> {
  return asOps(browser, readAiSwitch);
}

/** Sets the AI switch as OPS and checks the stored state. */
export async function setAiSwitchAsOps(browser: Browser, enabled: boolean): Promise<void> {
  await asOps(browser, async (page) => {
    const answer = await callApi(page, 'POST', '/api/ops/kill-switch', { enabled });
    expect(answer.status, 'OPS sets the switch').toBe(200);
    expect(await readAiSwitch(page)).toBe(enabled);
  });
}

/** Puts the AI back on as OPS. Used before and after every test that may change it. */
export async function ensureAiOn(browser: Browser): Promise<void> {
  await asOps(browser, async (page) => {
    if (!(await readAiSwitch(page))) {
      const answer = await callApi(page, 'POST', '/api/ops/kill-switch', { enabled: true });
      expect(answer.status, 'OPS turns the AI back on').toBe(200);
    }
    expect(await readAiSwitch(page), 'AI is on').toBe(true);
  });
}
