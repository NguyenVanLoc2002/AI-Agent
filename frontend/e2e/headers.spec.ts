import { CONTENT_SECURITY_POLICY } from '../config/securityHeaders';
import { CSP, EXPECTED } from './expectedHeaders';
import { expect, test } from './fixtures';

// FE-CSP (A-73) · the served bundle sends the enforced CSP and the other headers (2A, 3A) with the page and its
// assets, and the browser applies the policy: the controls below are blocked and reported. FE-DEPLOY (A-74): the
// bundle is served by the backend, which sends the same headers with every answer (API and errors too) and the
// cache rules of 3A; `vite preview`'s headers are checked by config/securityHeaders.test.ts.
// Framing is checked in framing.spec.ts (it needs its own browser launch options).

/** The asset URLs the page loads: script, stylesheet and one font named in the stylesheet. */
async function assetPaths(html: string, css: (path: string) => Promise<string>): Promise<string[]> {
  const script = /<script[^>]+src="([^"]+)"/.exec(html)?.[1];
  const sheet = /<link[^>]+rel="stylesheet"[^>]+href="([^"]+)"/.exec(html)?.[1];
  expect(script, 'script in index.html').toBeTruthy();
  expect(sheet, 'stylesheet in index.html').toBeTruthy();
  const font = /url\(([^)]+\.woff2)\)/.exec(await css(sheet ?? ''))?.[1]?.replace(/["']/g, '');
  expect(font, 'font in the stylesheet').toBeTruthy();
  return [script ?? '', sheet ?? '', font ?? ''];
}

test('the backend and `vite preview` send the same policy (review architecture M3)', async ({ request }) => {
  // Not a self-comparison: one value is the backend's answer, the other the config of `vite preview`.
  expect((await request.get('/')).headers()['content-security-policy']).toBe(CONTENT_SECURITY_POLICY);
  expect(CONTENT_SECURITY_POLICY).toBe(CSP);
});

test('the page and its assets carry the security headers', async ({ request, baseURL }) => {
  const page = await request.get('/');
  expect(page.status()).toBe(200);
  const html = await page.text();
  // No inline script or style that the policy would have to allow.
  expect(html).not.toMatch(/<script(?![^>]*\bsrc=)[^>]*>|<style|\sstyle=/);

  // Also a path that does not exist (a refusal: it needs a session), review QA.
  const assets = await assetPaths(html, async (path) => (await request.get(path)).text());
  for (const path of ['/', ...assets, '/__no-such-page__']) {
    const response = await request.get(path);
    expect(response.status(), path).toBeLessThan(500);
    // Review QA 9: answered at that path, not after a redirect.
    expect(response.url(), path).toBe(new URL(path, baseURL).toString());
    const headers = response.headers();
    for (const [name, value] of Object.entries(EXPECTED)) {
      expect(headers[name], `${name} on ${path}`).toBe(value);
    }
    // FE-DEPLOY (A-74, 3A): hashed assets are cached for a year; the page and everything else is never stored.
    const cache = headers['cache-control'] ?? '';
    if (assets.includes(path)) {
      expect(cache, `cache-control on ${path}`).toMatch(/max-age=31536000/);
      expect(cache, `cache-control on ${path}`).toMatch(/immutable/);
    } else {
      expect(cache, `cache-control on ${path}`).toMatch(/no-store/);
    }
    // HSTS only over HTTPS (tls.spec.ts).
    expect(headers['strict-transport-security'], path).toBeUndefined();
  }
});

test('the backend itself sends nosniff and a JSON type on API answers', async () => {
  // The backend serves the page too now (A-74), so these are its own answers.
  const backend = process.env.APAS_BACKEND_URL ?? 'http://127.0.0.1:8080';
  // Review security M1: script-src 'self' also trusts every same-origin /api path, so no API answer, including
  // the error ones, may be something a browser would run: nosniff and a JSON content type on each.
  for (const [path, status] of [['/api/auth/csrf', 200], ['/api/ops/kill-switch', 401], ['/api/no-such-endpoint', 401]] as const) {
    const response = await fetch(new URL(path, backend), { redirect: 'error' });
    expect(response.status, path).toBe(status);
    expect(response.headers.get('x-content-type-options'), path).toBe('nosniff');
    expect(response.headers.get('content-type') ?? '', path).toMatch(/^application\/(problem\+)?json\b/);
  }
});

// `eval` is not checked here: code run through `page.evaluate` (DevTools) is exempt from the eval rule, so the
// check could not fail. The policy's lack of 'unsafe-eval' is asserted by config/securityHeaders.test.ts.
test('controls: the browser blocks and reports an inline script and an inline style', async ({ page, cspViolations }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible();
  expect(cspViolations).toEqual([]);

  const ran = await page.evaluate(() => {
    const flags = window as unknown as { __inlineRan?: boolean };
    const script = document.createElement('script');
    script.textContent = 'window.__inlineRan = true;';
    document.head.append(script);
    const style = document.createElement('style');
    style.textContent = 'body { outline: 5px solid red; }';
    document.head.append(style);
    return { inline: flags.__inlineRan === true, outlined: getComputedStyle(document.body).outlineWidth === '5px' };
  });
  expect(ran).toEqual({ inline: false, outlined: false });

  // Both the page events and the browser's console messages (the fixture's backstop) report them.
  await expect.poll(() => [...new Set(cspViolations.map((v) => v.directive))].sort()).toEqual(['console', 'script-src-elem', 'style-src-elem']);
  cspViolations.length = 0; // expected here; the fixture checks the rest of the test
});
