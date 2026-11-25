import { randomUUID } from 'node:crypto';
import { defineConfig, devices } from '@playwright/test';
import { BASE_URL, FAKE_PROVIDER_URL } from './e2e/env';

// FE-E2E (A-71): browser tests against the real backend (profile `local`, which includes `demo`) and the local
// PostgreSQL of backend/compose.yaml. Run by hand (decision 2A); the backend and the database are started by
// the person running the tests, Playwright builds the app, serves the bundle with `vite preview` and starts the Fake
// provider (A-72). Chromium only (3A).
//
// Data safety (5A): the demo password comes only from APAS_DEMO_PASSWORD and is never written to a file.
// Traces and videos are off (a trace would hold the session cookie, the CSRF token and the typed password);
// a screenshot is kept only for a failed test, in test-results/ (ignored by git). The tests change the shared
// AI switch, so they run one at a time, every test leaves the AI on and the global teardown checks it again.

// FE-E2E-FLOWS review security M1: a token per run for the Fake provider's test endpoints. Made once in the
// runner; the workers inherit the environment, so they keep the same value.
process.env.APAS_E2E_FAKE_TOKEN ??= randomUUID().replaceAll('-', '') + randomUUID().replaceAll('-', '');

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/globalSetup.ts',
  globalTeardown: './e2e/globalTeardown.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  outputDir: './test-results',
  reporter: [['list']],
  use: {
    // The Secure session cookie is accepted over plain HTTP only on a loopback host (frontend/README.md).
    baseURL: BASE_URL,
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
    locale: 'vi-VN',
    timezoneId: 'UTC',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // Never reuse a server already on a port (review security L1): it could forward /api to another backend
  // than the one globalSetup checked, and the demo password is typed into it; a taken port fails the run.
  webServer: [
    { command: 'npm run build && npx vite preview', url: BASE_URL, reuseExistingServer: false, timeout: 180_000 },
    // FE-E2E-FLOWS (A-72): the Fake provider the backend calls (APAS_GATEWAY_PROVIDER_BASE_URL).
    {
      command: 'node e2e/fakeProvider.mjs', url: `${FAKE_PROVIDER_URL}/__e2e/health`, reuseExistingServer: false, timeout: 15_000,
      env: { APAS_E2E_FAKE_TOKEN: process.env.APAS_E2E_FAKE_TOKEN },
    },
  ],
});
