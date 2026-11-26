// The origins under test. No imports, so playwright.config.ts can use them too.
//
// FE-DEPLOY (A-74, 6A): the browser tests run against the deployed form of the app: the backend serving the built
// bundle (Maven profile with-frontend) on one origin, `APAS_BACKEND_URL`. Before A-74 they ran on `vite preview`
// (A-72: not the dev server, whose React StrictMode double effects raced a quote's confirmation token).
// Loopback only (A-71): the Secure session cookie is accepted over plain HTTP only there, and the demo password is
// typed into these pages.

const LOOPBACK = ['127.0.0.1', 'localhost', '[::1]'];

function loopback(name: string, value: string): string {
  if (!LOOPBACK.includes(new URL(value).hostname)) {
    throw new Error(`FE-E2E: ${name} must point to a loopback address.`);
  }
  return value.replace(/\/+$/, '');
}

export const BASE_URL = loopback('APAS_BACKEND_URL', process.env.APAS_BACKEND_URL ?? 'http://127.0.0.1:8080');

/**
 * FE-DEPLOY (A-74, 4A/6A): a second backend started with profile `tls` (HTTPS, self-signed keystore). When set,
 * the project `chromium-tls` runs e2e/tls.spec.ts against it; when not, that project is NOT RUN and globalSetup
 * says so.
 */
export const TLS_URL = process.env.APAS_BACKEND_TLS_URL ? loopback('APAS_BACKEND_TLS_URL', process.env.APAS_BACKEND_TLS_URL) : null;

// FE-E2E-FLOWS (A-72): the Fake provider started by Playwright. Start the backend with
// APAS_GATEWAY_PROVIDER_BASE_URL set to this address.
export const FAKE_PROVIDER_URL = 'http://127.0.0.1:18090';
