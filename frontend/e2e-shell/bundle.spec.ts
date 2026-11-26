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
