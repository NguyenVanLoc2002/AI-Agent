import { describe, expect, it } from 'vitest';
import { BUNDLE_HEADERS, COMMON_HEADERS, CONTENT_SECURITY_POLICY } from './securityHeaders';

// FE-CSP (A-73): the policy stays as strict as approved; a loosening has to change this test on purpose.

function directives(policy: string): Map<string, string[]> {
  return new Map(policy.split(';').map((part) => part.trim().split(/\s+/)).map(([name = '', ...values]) => [name, values]));
}

describe('security headers of the served bundle', () => {
  const csp = directives(CONTENT_SECURITY_POLICY);

  it('allows only the own origin for scripts, styles, fonts, images and requests', () => {
    for (const name of ['script-src', 'style-src', 'font-src', 'img-src', 'connect-src', 'form-action']) {
      expect(csp.get(name), name).toEqual(["'self'"]);
    }
  });

  it('denies everything else, framing, <base> and plugins', () => {
    for (const name of ['default-src', 'base-uri', 'frame-ancestors', 'object-src']) {
      expect(csp.get(name), name).toEqual(["'none'"]);
    }
  });

  it('has no unsafe source, wildcard, scheme source or extra directive', () => {
    expect(CONTENT_SECURITY_POLICY).not.toMatch(/unsafe-|\*|data:|blob:|https?:|'strict-dynamic'|nonce-|sha\d+-/);
    expect([...csp.keys()].sort()).toEqual(
      ['base-uri', 'connect-src', 'default-src', 'font-src', 'form-action', 'frame-ancestors', 'img-src', 'object-src', 'script-src', 'style-src'],
    );
  });

  it('sends the policy and the other headers with the bundle, and the common ones in development', () => {
    expect(BUNDLE_HEADERS).toEqual({
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      'Content-Security-Policy': CONTENT_SECURITY_POLICY,
      'X-Frame-Options': 'DENY',
      'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
      'Cross-Origin-Opener-Policy': 'same-origin',
      'Cross-Origin-Resource-Policy': 'same-origin',
    });
    expect(COMMON_HEADERS).toEqual({ 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' });
    expect(Object.keys(COMMON_HEADERS)).not.toContain('Content-Security-Policy');
  });
});

describe('vite.config.ts applies them (review QA: the dev server was checked by hand only)', () => {
  it('sends the bundle headers from `vite preview` and only the common ones from the dev server', async () => {
    const { default: config } = await import('../vite.config.ts');
    expect(config.preview?.headers).toEqual(BUNDLE_HEADERS);
    expect(config.server?.headers).toEqual(COMMON_HEADERS);
  });
});
