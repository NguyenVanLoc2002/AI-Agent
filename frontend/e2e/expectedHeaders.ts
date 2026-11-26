// FE-CSP (A-73) / FE-DEPLOY (A-74): the headers the served app must send, written out here and not read from
// config/securityHeaders.ts or the backend: a test comparing a server with the same object would pass whatever the
// object said (mutation CM1). Used by headers.spec.ts (HTTP) and tls.spec.ts (HTTPS).

export const CSP = "default-src 'none'; script-src 'self'; style-src 'self'; font-src 'self'; img-src 'self'; "
  + "connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'; object-src 'none'; worker-src 'none'";

export const EXPECTED: Readonly<Record<string, string>> = {
  'content-security-policy': CSP,
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'no-referrer',
  'x-frame-options': 'DENY',
  'permissions-policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
  'cross-origin-opener-policy': 'same-origin',
  'cross-origin-resource-policy': 'same-origin',
};

/** HSTS of the `tls` profile (A-74, 4A): one year, no subdomains, no preload. */
export const HSTS = 'max-age=31536000';
