// FE-CSP (A-73): the security headers of the served frontend, in one place for vite.config.ts and the tests.
//
// The built bundle (`vite preview`, decision 1A) gets an enforced Content-Security-Policy (2A) and the headers of
// 3A. The bundle has no inline script or style, no `data:` URL and self-hosted fonts, so every directive is
// 'self' or 'none'. React sets element styles through the CSSOM, which `style-src` does not restrict.
// Not set: Trusted Types (components/core/Icon.jsx writes SVG markup with dangerouslySetInnerHTML) and HSTS (the
// demo runs over plain HTTP on loopback). The backend serves the same policy with the built bundle (FE-DEPLOY, A-74:
// identity.SecurityConfiguration.CONTENT_SECURITY_POLICY); keep both equal (the browser tests check the backend's).
//
// The dev server gets no CSP (5A): Vite's hot reload needs an inline script. The deployed app is served by the
// backend (FE-DEPLOY, A-74); `vite preview` is for a local look at the bundle only.

export const CONTENT_SECURITY_POLICY = [
  "default-src 'none'",
  "script-src 'self'",
  "style-src 'self'",
  "font-src 'self'",
  "img-src 'self'",
  "connect-src 'self'",
  "form-action 'self'",
  "base-uri 'none'",
  "frame-ancestors 'none'",
  "object-src 'none'",
  // FE-DEPLOY (A-74, 5A): explicit; it would otherwise fall back to script-src 'self'.
  "worker-src 'none'",
].join('; ');

/** Sent by the dev server too: harmless for hot reload. */
export const COMMON_HEADERS: Readonly<Record<string, string>> = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
};

/** Sent with every response of the served bundle (`vite preview`). */
export const BUNDLE_HEADERS: Readonly<Record<string, string>> = {
  ...COMMON_HEADERS,
  'Content-Security-Policy': CONTENT_SECURITY_POLICY,
  'X-Frame-Options': 'DENY',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Resource-Policy': 'same-origin',
};
