import { createMockFetch, MOCK_MARKER } from './backend';

// FE-UI-P2 (A-80 D05): replaces window.fetch with the mock backend in the dev server only (`npm run dev:mock`).
// The banner in the console says so, so nobody mistakes the mock for the real backend.
// No DEV check here on purpose: main.tsx guards the import, and if that guard ever failed, this module must carry the
// marker into dist/ so scripts/verify-no-mocks.mjs fails the build (review security M2).
export function installMocks(): void {
  window.fetch = createMockFetch();
  console.info(`[${MOCK_MARKER}] Mock backend active: synthetic data, no real backend is called.`);
}
