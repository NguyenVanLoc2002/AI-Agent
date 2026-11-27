import { expect } from '@playwright/test';
import { test } from '../e2e/fixtures';

// FE-UI-P2: the production bundle (with react-router-dom's HashRouter, the shell and foundation.css) under the
// enforced Content-Security-Policy of `vite preview` (config/securityHeaders.ts, A-73). No backend runs here, so the
// app reaches its "session could not be checked" state; what this proves is that the bundle loads, routes by hash
// and renders with zero CSP violations (the shared fixture fails the test on any). The real-backend suite
// (`npm run e2e`) remains the evidence for the signed-in screens.

test('the built bundle loads under the enforced CSP and keeps a deep-link address', async ({ page }) => {
  const response = await page.goto('/#/hop-cho');
  expect(response?.headers()['content-security-policy']).toContain("default-src 'none'");

  await expect(page.getByText('Chưa kiểm tra được phiên đăng nhập')).toBeVisible();
  await expect(page).toHaveURL(/#\/hop-cho$/);
  expect(await page.evaluate(() => [...document.querySelectorAll('script')].every((s) => s.src !== '' && s.textContent === ''))).toBe(true);
  expect(await page.evaluate(() => document.querySelector('script[src*="mock" i]'))).toBeNull();
});

// FE-UI-P3 (P2 residual; not SEC evidence, review L-3): the signed-in screens of the production bundle under the same enforced CSP. The API is
// answered by Playwright (page.route) with synthetic data: this proves the screens render and call only their own
// origin without a CSP violation, not that the backend authorizes them (`npm run e2e` against the jar does that).
type Role = 'OPS' | 'SALES' | 'SECREV' | 'CUST';
const CONV = 'cv_AAAAAAAAAAAAAAAAAAAAAA';

const API: Record<Role, Record<string, unknown>> = {
  OPS: { '/api/ops/kill-switch': { aiEnabled: true, reason: null, since: '2026-11-23T08:41:00Z' } },
  SALES: { '/api/internal/handoffs': [{ handoffId: 1, conversationId: CONV, reasonCode: 'QUOTE_NOT_ISSUED', status: 'OPEN', openedAt: '2026-11-23T09:12:00Z', mine: false, followUpReasonCode: null }] },
  SECREV: { '/api/demo/security/events': { events: [], next: null } },
  CUST: {
    '/api/demo/conversations': { conversationId: CONV },
    [`/api/demo/conversations/${CONV}`]: { state: 'BOT' },
    [`/api/demo/conversations/${CONV}/messages`]: [],
  },
};

const HEADING: Record<Role, { path: string; name: string | RegExp }> = {
  OPS: { path: '/#/cong-tac-ai', name: 'Công tắc trợ lý AI' },
  SALES: { path: '/#/hop-cho', name: 'Hộp chờ nhân viên' },
  SECREV: { path: '/#/su-kien-bao-mat', name: 'Sự kiện bảo mật' },
  CUST: { path: '/#/chat', name: 'Tư vấn phụ tùng' },
};

for (const role of Object.keys(API) as Role[]) {
  test(`the signed-in ${role} screen renders from the built bundle under the enforced CSP`, async ({ page }) => {
    const requests: string[] = [];
    page.on('request', (request) => { requests.push(new URL(request.url()).origin); });
    await page.route('**/api/**', async (route) => {
      const path = new URL(route.request().url()).pathname;
      const body = path === '/api/auth/session' ? { role, customerLinked: role === 'CUST' }
        : path === '/api/auth/csrf' ? { headerName: 'X-CSRF-TOKEN', token: 'e2e-csrf-token' }
          : API[role][path];
      await route.fulfill(body === undefined
        ? { status: 404, contentType: 'application/problem+json', body: JSON.stringify({ code: 'NOT_FOUND' }) }
        : { status: path === '/api/demo/conversations' ? 201 : 200, contentType: 'application/json', body: JSON.stringify(body) });
    });

    await page.goto(HEADING[role].path);
    await expect(page.getByRole('heading', { level: 1, name: HEADING[role].name })).toBeAttached();
    await expect(page.getByRole('button', { name: 'Đăng xuất' })).toBeVisible();
    if (role === 'SALES') await expect(page.getByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ })).toBeVisible();
    if (role === 'OPS') await expect(page.getByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' })).toBeVisible();
    if (role === 'CUST') await expect(page.getByText('Trợ lý AI đang trả lời')).toBeVisible();
    expect([...new Set(requests)]).toEqual([new URL(page.url()).origin]);
  });
}
