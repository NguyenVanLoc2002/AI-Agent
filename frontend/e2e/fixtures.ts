import { test as base, expect, type BrowserContext } from '@playwright/test';

// FE-CSP (A-73, 4A): every page of every test reports Content-Security-Policy violations, and a test with one
// fails. Only the directive and the blocked URL (cut) are kept: they name what was blocked, never page content.
// Review security L3: the listener runs in the capture phase on window; browser console messages about the policy
// are a backstop (they also cover reports the page does not see); the pages are flushed before the check. Not seen:
// violations inside workers (the app has none) and frame-ancestors refusals (framing.spec.ts checks those).

export interface CspViolation {
  directive: string;
  blocked: string;
}

/** Starts collecting the violations of every page of `context` into `into`. */
export async function watchCsp(context: BrowserContext, into: CspViolation[]): Promise<void> {
  await context.exposeBinding('__apasCspViolation', (_source, violation: CspViolation) => { into.push(violation); });
  await context.addInitScript(() => {
    const report = (window as unknown as { __apasCspViolation: (v: { directive: string; blocked: string }) => void }).__apasCspViolation;
    window.addEventListener('securitypolicyviolation', (event) => {
      report({ directive: event.effectiveDirective, blocked: String(event.blockedURI).slice(0, 80) });
    }, true);
  });
  context.on('console', (message) => {
    if (message.type() === 'error' && message.text().includes('Content Security Policy')) {
      into.push({ directive: 'console', blocked: '' });
    }
  });
}

/** Lets reports still in flight arrive, then fails on any. Pages that were closed or are navigating are skipped. */
export async function expectNoViolations(context: BrowserContext, violations: CspViolation[]): Promise<void> {
  await Promise.all(context.pages().map((page) => page.evaluate(() => 0).catch(() => 0)));
  expect(violations, 'Content-Security-Policy violations').toEqual([]);
}

/** `test` with the CSP watch on the default browser context of each test. */
export const test = base.extend<{ cspViolations: CspViolation[] }>({
  cspViolations: [async ({ context }, use) => {
    const violations: CspViolation[] = [];
    await watchCsp(context, violations);
    await use(violations);
    await expectNoViolations(context, violations);
  }, { auto: true }],
});

export { expect };
