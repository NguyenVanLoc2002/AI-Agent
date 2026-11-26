import { EXPECTED, HSTS } from './expectedHeaders';
import { expect, test } from './fixtures';
import { signInWithForm } from './support';

// FE-DEPLOY (A-74, 4A/6A) · the backend with profile `tls` (HTTPS, local self-signed keystore): every answer carries
// the full header set, HSTS and the CSP with upgrade-insecure-requests; a sign-in works with the Secure session
// cookie. Runs only in the project `chromium-tls` (APAS_BACKEND_TLS_URL; APAS_E2E_REQUIRE_TLS=1 makes it mandatory).
// The certificate errors of the self-signed keystore are ignored there, nothing else is relaxed.

test('HTTPS answers carry the full header set, HSTS and the upgrade directive', async ({ request, baseURL }) => {
  expect(baseURL ?? '', 'the TLS project runs over HTTPS').toMatch(/^https:\/\//);
  const html = await (await request.get('/')).text();
  const script = /<script[^>]+src="([^"]+)"/.exec(html)?.[1];
  expect(script, 'script in index.html').toBeTruthy();
  // Review QA 4: page, asset, API answer, refusal and a missing asset.
  for (const path of ['/', script ?? '', '/api/auth/csrf', '/api/ops/kill-switch', '/assets/missing-0000.js']) {
    const response = await request.get(path);
    expect(response.status(), path).toBeLessThan(500);
    const headers = response.headers();
    for (const [name, value] of Object.entries(EXPECTED)) {
      const expected = name === 'content-security-policy' ? `${value}; upgrade-insecure-requests` : value;
      expect(headers[name], `${name} on ${path}`).toBe(expected);
    }
    expect(headers['strict-transport-security'], path).toBe(HSTS);
  }
});

test('a staff member signs in and out over HTTPS', async ({ page }) => {
  await signInWithForm(page, 'ops-1');
  await expect(page.getByRole('heading', { name: 'Công tắc trợ lý AI', level: 1 })).toBeVisible();
  const cookie = (await page.context().cookies()).find((c) => c.name === 'JSESSIONID');
  expect(cookie?.secure, 'session cookie is Secure').toBe(true);
  expect(cookie?.httpOnly, 'session cookie is HttpOnly').toBe(true);
  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  await expect(page.getByText('Bạn đã đăng xuất.')).toBeVisible();
});
