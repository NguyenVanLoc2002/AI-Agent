import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';

// FE-CSP (A-73) · framing: CSP frame-ancestors 'none' stops another origin from framing the app. X-Frame-Options DENY
// is the fallback for browsers without CSP (ignored when frame-ancestors is present: removing only it, or only
// Cross-Origin-Resource-Policy, which does not apply to page loads, leaves this test green; headers.spec.ts pins
// both). Removing frame-ancestors turns it red (mutation CM4). Chromium's Local Network Access check would block the
// intercepted parent's frame on its own (ERR_BLOCKED_BY_LOCAL_NETWORK_ACCESS_CHECKS) and hide the headers' effect,
// so it is switched off for this file (a test-browser flag only; launch options force their own worker).
test.use({ launchOptions: { args: ['--disable-features=LocalNetworkAccessChecks'] } });

/**
 * Opens a parent page on another loopback origin (http://localhost:4173, intercepted, no policy of its own) that
 * frames `target`. An about:blank parent cannot be used: Chromium refuses that frame whatever the headers say.
 */
async function frameFromOtherOrigin(page: Page, appUrl: string, target: string): Promise<void> {
  const host = new URL(appUrl);
  host.hostname = 'localhost';
  host.pathname = '/__frame-host__';
  await page.route(host.toString(), (route) => route.fulfill({
    contentType: 'text/html; charset=utf-8', body: `<!doctype html><iframe id="frame" src="${target}" width="800" height="600"></iframe>`,
  }));
  await page.goto(host.toString());
}

/** The URL of the parent's one child frame, once it exists. */
async function childFrameUrl(page: Page): Promise<string> {
  await expect.poll(() => page.frames().length).toBe(2);
  return page.frames()[1]?.url() ?? '';
}

test('the app refuses to be framed by another origin', async ({ page, baseURL, cspViolations }) => {
  const app = baseURL ?? '';
  await frameFromOtherOrigin(page, app, `${app}/`);
  // Positive evidence of the refusal (review QA): Chromium replaces the refused frame with its error page.
  await expect.poll(() => childFrameUrl(page)).toMatch(/^chrome-error:/);
  await expect(page.frameLocator('#frame').getByRole('heading', { name: 'Đăng nhập' })).toHaveCount(0);
  // The refusal is the one expected report (the browser's console message, seen by the fixture's backstop).
  await expect.poll(() => cspViolations.map((v) => v.directive)).toEqual(['console']);
  cspViolations.length = 0;
});

test('control: the same app page loads in the same frame once its framing headers are removed', async ({ page, baseURL }) => {
  const app = baseURL ?? '';
  // The real page from the real server, only CSP and X-Frame-Options stripped (review QA): the headers are the
  // one difference from the test above.
  await page.route(`${app}/`, async (route) => {
    const response = await route.fetch();
    const headers = { ...response.headers() };
    delete headers['content-security-policy'];
    delete headers['x-frame-options'];
    await route.fulfill({ response, headers });
  });
  await frameFromOtherOrigin(page, app, `${app}/`);
  await expect(page.frameLocator('#frame').getByRole('heading', { name: 'Đăng nhập' })).toHaveCount(1, { timeout: 15_000 });
  expect(await childFrameUrl(page)).toBe(`${app}/`);
});
