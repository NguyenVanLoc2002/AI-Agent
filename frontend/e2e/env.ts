// FE-E2E (A-71): the page origin under test. Loopback only: the Secure session cookie is accepted over plain
// HTTP only there (frontend/README.md). No imports, so playwright.config.ts can use it too.
// A-72: the production bundle served by `vite preview` (vite.config.ts `preview`), which forwards /api like the
// dev server. The dev server runs React StrictMode effects twice, which made a quote read twice on a reload and
// raced its confirmation token; the bundle runs them once, as users get it.
export const BASE_URL = 'http://127.0.0.1:4173';

// FE-E2E-FLOWS (A-72): the Fake provider started by Playwright. Start the backend with
// APAS_GATEWAY_PROVIDER_BASE_URL set to this address.
export const FAKE_PROVIDER_URL = 'http://127.0.0.1:18090';
