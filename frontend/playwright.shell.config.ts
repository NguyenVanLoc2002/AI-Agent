import { defineConfig, devices } from '@playwright/test';

// FE-UI-P2 (P2-T14): layout checks of the app shell in a real browser, with no backend and no password. Playwright
// starts the dev server in mock mode (`npm run dev:mock`, src/mocks/, dev only, A-80 D05) on 127.0.0.1:5174.
// Covers what jsdom cannot: widths 390/1024/1280/1440, horizontal overflow, page box, focus in forced colours.
// The mock dev server sends no CSP; project bundle-csp loads the production build under the enforced CSP instead.
// Neither is a backend test or evidence of authorization; the real-backend suite stays `npm run e2e`.
const PORT = 5174;
const PREVIEW_PORT = 4173;

export default defineConfig({
  testDir: './e2e-shell',
  fullyParallel: true,
  retries: 0,
  forbidOnly: true,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  outputDir: './test-results/shell',
  reporter: [['list']],
  use: {
    baseURL: `http://127.0.0.1:${PORT}`,
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
    locale: 'vi-VN',
    timezoneId: 'UTC',
  },
  projects: [
    { name: 'shell-mock', testMatch: 'shell.spec.ts', use: { ...devices['Desktop Chrome'] } },
    // The production bundle under the enforced CSP of `vite preview` (127.0.0.1:4173, vite.config.ts); no backend.
    { name: 'bundle-csp', testMatch: 'bundle.spec.ts', use: { ...devices['Desktop Chrome'], baseURL: `http://127.0.0.1:${PREVIEW_PORT}` } },
  ],
  webServer: [
    { command: 'npm run dev:mock', url: `http://127.0.0.1:${PORT}/`, reuseExistingServer: false, timeout: 60_000 },
    { command: 'npm run build && npx vite preview', url: `http://127.0.0.1:${PREVIEW_PORT}/`, reuseExistingServer: false, timeout: 180_000 },
  ],
});
