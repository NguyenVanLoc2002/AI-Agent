/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';
import { configDefaults } from 'vitest/config';
import { BUNDLE_HEADERS, COMMON_HEADERS } from './config/securityHeaders.ts';

// EP-10a (decision 4A): the dev server forwards /api to the backend on the loopback address, so the page
// and the API share one origin. No CORS on the backend, no change to its security configuration. Both
// servers listen on 127.0.0.1 only (the demo profile refuses other bind addresses, A-39(b)).
const backend = process.env.APAS_BACKEND_URL ?? 'http://127.0.0.1:8080';
// The proxy carries the session cookie over plain HTTP: only a loopback backend is accepted.
if (!['127.0.0.1', 'localhost', '[::1]'].includes(new URL(backend).hostname)) {
  throw new Error('APAS_BACKEND_URL must point to a loopback address');
}

// FE-UI-P2 (review security M1): the dev-only mock must never be built. A build in mode "mock", or with NODE_ENV set to
// anything but "production" (which would make import.meta.env.DEV true), is refused before anything is bundled.
const refuseMockBuild: Plugin = {
  name: 'apas-refuse-mock-build',
  config(_config, { command, mode }) {
    if (command === 'build' && (mode === 'mock' || (process.env.NODE_ENV !== undefined && process.env.NODE_ENV !== 'production'))) {
      throw new Error('Refusing to build: mock mode or NODE_ENV is not production (src/mocks/ is dev-only).');
    }
  },
};

export default defineConfig({
  plugins: [react(), refuseMockBuild],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    // FE-CSP (A-73, 5A): no CSP in development, hot reload needs an inline script.
    headers: { ...COMMON_HEADERS },
    proxy: {
      '/api': { target: backend, changeOrigin: false },
    },
  },
  // FE-CSP (A-73, 1A): the built bundle is served with the enforced CSP and the other security headers.
  preview: { host: '127.0.0.1', port: 4173, strictPort: true, headers: { ...BUNDLE_HEADERS } },
  // FE-DEPLOY (A-74, review architecture L1): written out, because the backend opens exactly /assets/** without a
  // session (identity.FrontendPaths) and caches it (platform.internal.StaticResourceConfiguration).
  base: '/',
  build: { sourcemap: false, assetsDir: 'assets' },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Dates on screen use the viewer's time zone; tests pin it so the expected text is stable.
    env: { TZ: 'UTC' },
    restoreMocks: true,
    // Browser tests run with Playwright, not Vitest: against the real backend (`npm run e2e`, FE-E2E) and the shell
    // against the dev mock (`npm run e2e:shell`, FE-UI-P2).
    exclude: [...configDefaults.exclude, 'e2e/**', 'e2e-shell/**'],
  },
});
