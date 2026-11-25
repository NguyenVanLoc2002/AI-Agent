/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import { configDefaults } from 'vitest/config';

// EP-10a (decision 4A): the dev server forwards /api to the backend on the loopback address, so the page
// and the API share one origin. No CORS on the backend, no change to its security configuration. Both
// servers listen on 127.0.0.1 only (the demo profile refuses other bind addresses, A-39(b)).
const backend = process.env.APAS_BACKEND_URL ?? 'http://127.0.0.1:8080';
// The proxy carries the session cookie over plain HTTP: only a loopback backend is accepted.
if (!['127.0.0.1', 'localhost', '[::1]'].includes(new URL(backend).hostname)) {
  throw new Error('APAS_BACKEND_URL must point to a loopback address');
}

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
    },
  },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true },
  build: { sourcemap: false },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Dates on screen use the viewer's time zone; tests pin it so the expected text is stable.
    env: { TZ: 'UTC' },
    restoreMocks: true,
    // Browser tests against the real backend run with Playwright (`npm run e2e`, FE-E2E), not with Vitest.
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
});
