import { expect, test, type Browser, type Cookie } from '@playwright/test';
import { callApi, demoPassword, ensureAiOn, expectProblem, loginAnswer, readAiSwitchAsOps, signInWithApi, signInWithForm, type ApiAnswer } from './support';

// FE-E2E (A-71) · sign-in, missing session, CSRF and sign-out (ADR-010) through the real backend.

const SESSION_EXPIRED = 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
const BAD_CREDENTIALS = 'Tên đăng nhập hoặc mật khẩu không đúng.';
// A constant, not derived from the real password, so a failing assertion can never print it (review
// security M3). Used on a customer account, so a future login lockout (UX-12) cannot lock out ops-1, which
// the clean-up needs (review security L6, QA M6).
const WRONG_PASSWORD = 'sai-mat-khau-e2e-000';

test.beforeAll(async ({ browser }) => { await ensureAiOn(browser); });
test.afterEach(async ({ browser }) => { await ensureAiOn(browser); });

test('a wrong password and an unknown user get the same answer, on screen and at the API', async ({ page }) => {
  expect(WRONG_PASSWORD === demoPassword(), 'the wrong password differs from the demo password').toBe(false);
  for (const username of ['cust-a', 'khong-ton-tai']) {
    await signInWithForm(page, username, WRONG_PASSWORD);
    await expect(page.getByRole('alert').filter({ hasText: BAD_CREDENTIALS })).toBeVisible();
    expect(await page.getByLabel('Mật khẩu').inputValue() === '', 'password field cleared').toBe(true);
    await expect(page.getByRole('button', { name: 'Đăng xuất' })).toHaveCount(0);
  }
  // The screen could map any error to one sentence; the backend answers must not differ either (review QA H2).
  const known = await loginAnswer(page, 'cust-a', WRONG_PASSWORD);
  const unknown = await loginAnswer(page, 'khong-ton-tai', WRONG_PASSWORD);
  expectProblem(known, 401, 'UNAUTHENTICATED');
  expect(unknown).toEqual(known);
});

test('without a session the switch API answers 401 and changes nothing', async ({ page, browser }) => {
  await page.goto('/');
  expectProblem(await callApi(page, 'GET', '/api/ops/kill-switch'), 401, 'UNAUTHENTICATED');
  expectProblem(await callApi(page, 'POST', '/api/ops/kill-switch', { enabled: false }), 401, 'UNAUTHENTICATED');
  expect(await readAiSwitchAsOps(browser)).toBe(true);
});

test('OPS cannot change the switch without a CSRF token', async ({ page, browser }) => {
  await signInWithApi(page, 'ops-1');
  expectProblem(await callApi(page, 'POST', '/api/ops/kill-switch', { enabled: false }, false), 403, 'FORBIDDEN');
  expect(await readAiSwitchAsOps(browser)).toBe(true);
});

// Simulates a lost session cookie. It shows how the screen handles a 401; the server-side idle (30 min) and
// absolute (8 h) limits are not exercised here (review QA M3).
test('when the session cookie is gone, the switch screen returns to sign-in and changes nothing', async ({ page, context, browser }) => {
  await signInWithForm(page, 'ops-1');
  const toggle = page.getByRole('switch', { name: 'Trợ lý AI' });
  await expect(toggle).toHaveAttribute('aria-checked', 'true');

  await context.clearCookies();
  await toggle.click();
  const write = page.waitForResponse((r) => r.url().endsWith('/api/ops/kill-switch') && r.request().method() === 'POST');
  await page.getByRole('alertdialog', { name: 'Tắt trợ lý AI?' }).getByRole('button', { name: 'Tắt AI' }).click();
  expect((await write).status()).toBe(401);
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible();
  await expect(page.getByText(SESSION_EXPIRED)).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Công tắc trợ lý AI' })).toHaveCount(0);

  expect(await readAiSwitchAsOps(browser)).toBe(true);
});

/** Replays copied cookies in a fresh browser context and calls the switch API with them. */
async function withReplayedCookies(browser: Browser, cookies: Cookie[], method: 'GET' | 'POST'): Promise<ApiAnswer> {
  const replay = await browser.newContext();
  try {
    await replay.addCookies(cookies);
    const page = await replay.newPage();
    await page.goto('/');
    return await callApi(page, method, '/api/ops/kill-switch', method === 'POST' ? { enabled: true } : undefined);
  } finally {
    await replay.close();
  }
}

test('sign-out ends the session on the server, not only on the screen', async ({ page, context, browser }) => {
  await signInWithForm(page, 'ops-1');
  await expect(page.getByRole('heading', { name: 'Công tắc trợ lý AI', level: 1 })).toBeVisible();
  const before = await context.cookies();

  // Control (review security M2): the copied cookies do carry the session before the sign-out. The write
  // turns the AI on while it is on already, so it changes nothing.
  expect((await withReplayedCookies(browser, before, 'GET')).status).toBe(200);
  expect((await withReplayedCookies(browser, before, 'POST')).status).toBe(200);

  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  await expect(page.getByText('Bạn đã đăng xuất.')).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible();

  // The same cookies no longer bring the session back, for a read or a write.
  expectProblem(await withReplayedCookies(browser, before, 'GET'), 401, 'UNAUTHENTICATED');
  expectProblem(await withReplayedCookies(browser, before, 'POST'), 401, 'UNAUTHENTICATED');
  expect(await readAiSwitchAsOps(browser)).toBe(true);
});
