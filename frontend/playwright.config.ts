import { defineConfig, devices } from '@playwright/test';
import { BASE_URL } from './e2e/env';

// FE-E2E (A-71): browser tests against the real backend (profile `local`, which includes `demo`) and the local
// PostgreSQL of backend/compose.yaml. Run by hand (decision 2A); the backend and the database are started by
// the person running the tests, Playwright starts only the Vite dev server. Chromium only (3A).
//
// Data safety (5A): the demo password comes only from APAS_DEMO_PASSWORD and is never written to a file.
// Traces and videos are off (a trace would hold the session cookie, the CSRF token and the typed password);
// a screenshot is kept only for a failed test, in test-results/ (ignored by git). The tests change the shared
// AI switch, so they run one at a time, every test leaves the AI on and the global teardown checks it again.

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
  webServer: {
    command: 'npm run dev',
    url: BASE_URL,
    // Never reuse a server already on the port (review security L1): it could forward /api to another backend
    // than the one globalSetup checked, and the demo password is typed into it. Vite's strictPort then fails
    // the run instead.
    reuseExistingServer: false,
    timeout: 60_000,
  },
});
